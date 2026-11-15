package com.haooz.chedule.ui.effects.ripple

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.dp
import com.haooz.softglass.platform.asComposeShader
import com.haooz.softglass.platform.createGlassRuntimeShader
import com.haooz.softglass.platform.isRuntimeShaderSupported

/** 折射层是否需要 runtime shader。 */
fun supportsWaterRipple(): Boolean = isRuntimeShaderSupported()

/**
 * 参考实现 `oobe_glow.agsl` 的整屏 shader 里 `uTime` 以秒计，而这里把整段进场压进
 * 一次 [RIPPLE_DURATION_MILLIS] 的 0→1 进度动画，所以换算关系是
 * `uTime(秒) = 进度 * 1.6`（1.6s = 下面动画的时长）。
 */
private const val REFERENCE_SECONDS = 1.6f

/** 光圈参数，照抄 `OobeGlowPainter` 的 uniform 取值。 */
private const val CIRCLE_SPEED = 0.9f
private const val CIRCLE_EASING = 1.4f
private const val CIRCLE_THICKNESS = 0.4f
private const val CIRCLE_FINAL_RADIUS = 1.0f
private const val CIRCLE_COLOR_FREQ = 1.0f
private const val CIRCLE_COLOR_SPEED = 0.0f
private const val CIRCLE_COLOR_OFFSET = 0.25f
/** 参考的 `uCircleAddBlend`：Screen 之外再按这个比例做一次加色。 */
private const val CIRCLE_ADD_BLEND = 0.04f

/** 开场圆形遮罩参数（单位为秒）。 */
private const val MASK_DELAY = 0.3f
private const val MASK_THICKNESS = 0.3f
private const val MASK_SPEED = 0.9f

/** 水波与遮罩的总时长（毫秒）。 */
private const val RIPPLE_DURATION_MILLIS = 1600

/**
 * 欢迎页的启动水波 + 开场圆形遮罩：移植 HyperOS 开机引导里的扩散光环与揭幕
 * （`oobe_glow.agsl` 的 circle 段与 mask 段）。
 *
 * 两层各走各的时间轴（照抄参考，不是同步的）：
 * - 光环：`animatedTime = t * 0.9`，**从 0s 就开始扩**，`t/(k - t(k-1))`（k=1.4）缓动，
 *   1.11s 扩到屏幕边缘；
 * - 遮罩：`easeOutQuad(min(t*0.9 - 0.3, 1))`，**0.3s 才起步**，1.44s 铺满全屏。
 * 所以光环始终跑在遮罩边缘前面一圈，从黑里透出来才看得见。
 *
 * 只播一次，播完整层撤掉（之后 morph 才可能被触发）。
 */
@Composable
fun WaterRippleOverlay(
    progress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
    durationMillis: Int = RIPPLE_DURATION_MILLIS,
) {
    if (!supportsWaterRipple()) return

    // 播完就整层撤掉，之后不再跑全屏 shader
    var finished by remember(progress) { mutableStateOf(false) }

    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        finished = true
    }

    if (finished) return

    val shader = remember { createGlassRuntimeShader(WATER_RIPPLE_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader.asComposeShader()) }
    val maskShader = remember { createGlassRuntimeShader(WATER_RIPPLE_MASK_SHADER) }
    val maskBrush = remember(maskShader) { ShaderBrush(maskShader.asComposeShader()) }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 与参考一致：center = vec2(0.5, 0.5 - uCircleYOffset)，uCircleYOffset = 0.1
        val centerY = 0.4f
        // 参考 shader 的 uTime 以秒计：进度 0→1 折成 0→1.6s
        val seconds = progress.value * REFERENCE_SECONDS

        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uCenterY", centerY)
        shader.setFloatUniform("uTime", seconds)
        shader.setFloatUniform("uCircleSpeed", CIRCLE_SPEED)
        shader.setFloatUniform("uCircleEasing", CIRCLE_EASING)
        shader.setFloatUniform("uCircleThickness", CIRCLE_THICKNESS)
        shader.setFloatUniform("uCircleFinalRadius", CIRCLE_FINAL_RADIUS)
        shader.setFloatUniform("uCircleColorFreq", CIRCLE_COLOR_FREQ)
        shader.setFloatUniform("uCircleColorSpeed", CIRCLE_COLOR_SPEED)
        shader.setFloatUniform("uCircleColorOffset", CIRCLE_COLOR_OFFSET)
        // Screen 混合：在淡蓝流光上呈现为扩散的水波高光
        drawRect(brush, blendMode = BlendMode.Screen)
        // 参考还有一层 uCircleAddBlend = 0.04 的加色
        drawRect(brush, alpha = CIRCLE_ADD_BLEND, blendMode = BlendMode.Plus)

        // 开场遮罩：黑色盖在整页之上（含流光背景与内容），随圆半径扩大把画面揭开
        maskShader.setFloatUniform("uResolution", size.width, size.height)
        maskShader.setFloatUniform("uCenterY", centerY)
        maskShader.setFloatUniform("uTime", seconds)
        maskShader.setFloatUniform("uMaskDelay", MASK_DELAY)
        maskShader.setFloatUniform("uMaskThickness", MASK_THICKNESS)
        maskShader.setFloatUniform("uMaskSpeed", MASK_SPEED)
        drawRect(maskBrush)
    }
}

private const val WATER_RIPPLE_SHADER = """
uniform float2 uResolution;
uniform float uTime;
uniform float uCenterY;
uniform float uCircleSpeed;
uniform float uCircleEasing;
uniform float uCircleThickness;
uniform float uCircleFinalRadius;
uniform float uCircleColorFreq;
uniform float uCircleColorSpeed;
uniform float uCircleColorOffset;

const float TWO_PI = 6.2831853;

vec3 pal(float t) {
    return vec3(0.5) + vec3(0.5) * cos(TWO_PI * (vec3(t) + vec3(0.0, 0.1, 0.2)));
}

half4 main(float2 fragCoord) {
    vec2 uv = fragCoord / uResolution;
    // 以短边为 1 的坐标系算距离，保证是正圆而不是椭圆
    vec2 aspect = uResolution.x > uResolution.y
        ? vec2(1.0, uResolution.y / uResolution.x)
        : vec2(uResolution.x / uResolution.y, 1.0);
    vec2 center = vec2(0.5, uCenterY);
    float dist = length((uv - center) * aspect);

    // 与参考一致：animatedTime = t * uCircleSpeed，超过 1 表示光圈已跑完
    float animatedTime = uTime * uCircleSpeed;
    if (animatedTime > 1.0) {
        return half4(0.0);
    }
    // 参考的缓动：t / (k - t*(k-1))，起步慢、后段追上
    float k = clamp(uCircleEasing, 0.0001, 10000.0);
    animatedTime = clamp(animatedTime / (k - animatedTime * (k - 1.0)), 0.0, 1.0);

    float outerRadius = animatedTime * uCircleFinalRadius;
    float innerRadius = outerRadius - uCircleThickness;
    // map(dist, outer, inner, 0, 1)：内缘 1、外缘 0
    float g = clamp((dist - outerRadius) / (innerRadius - outerRadius), 0.0, 1.0);
    // 参考的环带颜色：调色板 × parabola(g, 3)，中间最亮、两头消失
    float band = pow(4.0 * g * (1.0 - g), 3.0);
    // 只让环带出颜色：g=1（环内）必须是 0，否则整个圆盘会被不透明的淡白色铺满，
    // 盖住下面的流光背景，看起来就成了「扩散结束流光才出现」
    float ringAlpha = clamp(band * 2.0, 0.0, 1.0);
    // 淡蓝色调：调色板压到冷色区间，再向白色收
    vec3 palette = pal(g * uCircleColorFreq + uTime * uCircleColorSpeed + uCircleColorOffset);
    vec3 ringColor = mix(vec3(0.55, 0.70, 1.0), vec3(1.0), clamp(palette.r * 0.6 + 0.4, 0.0, 1.0));
    ringColor = mix(ringColor, vec3(1.0), band * 0.75);
    vec3 c = clamp(ringColor, 0.0, 1.0);
    // 预乘 alpha：Screen 混合按预乘语义处理
    return half4(c * ringAlpha, ringAlpha);
}
"""

/**
 * 开场圆形遮罩，照抄 `oobe_glow.agsl` 结尾的 mask 段：
 *
 * `radius = easeOutQuad(min(uTime*0.9 - uMaskDelay, 1.0))`
 * `mask = 1.0 - smoothstep(radius - uMaskThickness, radius + uMaskThickness, dist)`
 *
 * 参考把遮罩乘在整屏颜色上（`mix(vec3(0.0), finalColor, mask)`），这里输出等价的一层黑：
 * 遮罩内透明、遮罩外不透明，扣在欢迎页最上层即把画面「揭」出来。
 */
private const val WATER_RIPPLE_MASK_SHADER = """
uniform float2 uResolution;
uniform float uTime;
uniform float uCenterY;
uniform float uMaskDelay;
uniform float uMaskThickness;
uniform float uMaskSpeed;

float easeOutQuad(float x) {
    return 1.0 - (1.0 - x) * (1.0 - x);
}

half4 main(float2 fragCoord) {
    vec2 uv = fragCoord / uResolution;
    // 与光环同一套坐标：短边为 1，圆心同样是应用图标中心
    vec2 aspect = uResolution.x > uResolution.y
        ? vec2(1.0, uResolution.y / uResolution.x)
        : vec2(uResolution.x / uResolution.y, 1.0);
    vec2 center = vec2(0.5, uCenterY);
    float dist = length((uv - center) * aspect);

    // uTime 已是秒，与参考完全一致
    float radius = uTime * uMaskSpeed - uMaskDelay;
    radius = easeOutQuad(min(radius, 1.0));
    float mask = 1.0 - smoothstep(radius - uMaskThickness, radius + uMaskThickness, dist);

    return half4(0.0, 0.0, 0.0, 1.0 - mask);
}
"""
