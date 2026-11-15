/*
 * 通用的「连续弯曲」形变：按一个轴对整层内容做逐行/逐列缩放，用一次 runtime shader 采样完成，
 * 不录制位图、不逐列绘制。任何页面都可以挂 [Modifier.arcBend]。
 *
 * [ArcBendAxis.Horizontal]：按 x 分列做纵向缩放，左右两端形状不同 —— 配绕纵轴（rotationY）翻转。
 * [ArcBendAxis.Vertical]：按 y 分行做横向缩放，上下两端形状不同 —— 配绕横轴（rotationX）翻转。
 * 轴必须与翻转轴对应，否则「上下翻却是左右弯」，看着像扭了一下。
 *
 * 目标像素被缩放 s 倍，故反查源像素 src = center + (p - center) / s。按坐标连续求值，
 * 整条弧线没有台阶。Android 走 AGSL，Skia 后端（iOS / 鸿蒙 / 桌面）走 SkSL，同一份源码。
 */
package com.haooz.chedule.ui.effects.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/** 可选的弯曲曲线。 */
enum class ArcBendShape {
    /**
     * 一端内凹 → 另一端鼓起：`s = 1 + a·(2u²−1)`，二阶导恒正，整条单曲率无拐点。
     * [Modifier.arcBend] 的 amplitude 是**相对半轴的无量纲比例**（建议 0.2 ~ 0.5）。
     */
    InwardOutward,

    /**
     * 末段 u³ 收缩：`s = 1 − a·u³/radius`。
     * amplitude 是**绝对像素收缩量**（调用方自己乘 density），radius 是当前轴的半径。
     */
    RightShrink,
}

/** 弯曲沿哪个轴变化；必须与调用方的翻转轴一致。 */
enum class ArcBendAxis {
    /** 按 x 变化、纵向缩放；配 `rotationY`。 */
    Horizontal,

    /** 按 y 变化、横向缩放；配 `rotationX`。 */
    Vertical,
}

/**
 * `content` 由平台通过 RenderEffect 绑定为本层内容，不需要录制位图。
 * `resolution` 是图层像素尺寸（不是 dp）。
 */
internal const val ARC_BEND_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float amplitude;
uniform float bendShape;
uniform float bendAxis;

half4 main(float2 p) {
    float cx = resolution.x * 0.5;
    float cy = resolution.y * 0.5;
    float u;
    float radius;
    if (bendAxis > 0.5) {
        u = clamp(p.y / max(resolution.y, 1.0), 0.0, 1.0);
        radius = cx;
    } else {
        u = clamp(p.x / max(resolution.x, 1.0), 0.0, 1.0);
        radius = cy;
    }
    float s;
    if (bendShape > 0.5) {
        // 2u²−1：起点 -1（内凹）→ 终点 +1（鼓起）。起点导数为 0 起步平直，
        // 二阶导恒正、整条一个弯向；别换成带拐点的 S 曲线。
        s = 1.0 + amplitude * (2.0 * u * u - 1.0);
    } else {
        s = 1.0 - (amplitude * u * u * u) / max(radius, 1.0);
    }
    s = max(s, 0.2);
    if (bendAxis > 0.5) {
        float sx = cx + (p.x - cx) / s;
        if (sx < 0.0 || sx >= resolution.x) return half4(0.0, 0.0, 0.0, 0.0);
        return content.eval(float2(sx, p.y));
    }
    float sy = cy + (p.y - cy) / s;
    if (sy < 0.0 || sy >= resolution.y) return half4(0.0, 0.0, 0.0, 0.0);
    return content.eval(float2(p.x, sy));
}
"""

/** 平台 shader 句柄：每个使用点持有一份，避免同一帧两处的 uniform 互相覆盖。 */
internal interface ArcBendShader {
    fun update(width: Float, height: Float, amplitude: Float, shape: ArcBendShape, axis: ArcBendAxis)
    fun renderEffect(): RenderEffect?
}

/**
 * 能力探测（含 shader 编译）。false 时调用方应自行降级 —— 这个 Modifier 不猜降级策略。
 * 结果按平台缓存，只在组合期调用一次。公开给复用方决定自己的兜底动画。
 */
expect fun arcBendSupported(): Boolean

/** 为本使用点创建一份 shader；不支持或编译失败返回 null。 */
internal expect fun createArcBendShader(): ArcBendShader?

/**
 * 给整层内容挂上连续弯曲。进度、曲线、幅度、轴向都由调用方给，本 Modifier 不持有任何状态机。
 *
 * ```
 * Box(
 *     Modifier.arcBend(
 *         shape = ArcBendShape.InwardOutward,
 *         amplitude = { 0.35f * sin(PI * progress) },
 *         axis = ArcBendAxis.Vertical,
 *         visible = { progress > 0f },
 *     )
 * ) { content() }
 * ```
 *
 * - amplitude 只在绘制阶段读，不会逐帧重组；≤ 0.001 时该帧不挂效果。
 * - [axis] 默认 [ArcBendAxis.Horizontal]（引导页的 rotationY 翻转）；上下翻的页面必须传 Vertical。
 * - [visible] 为 false 时连内容一起跳过绘制（转场里常有一半时间只画一面）。
 * - 不支持 shader 的平台本 Modifier 退化为原样绘制，降级由调用方用 [arcBendSupported] 决定。
 */
@Composable
fun Modifier.arcBend(
    shape: ArcBendShape,
    amplitude: () -> Float,
    axis: ArcBendAxis = ArcBendAxis.Horizontal,
    visible: () -> Boolean = { true },
): Modifier {
    val shader = remember { createArcBendShader() }
    if (shader == null) return this
    return this
        .graphicsLayer {
            renderEffect = if (visible()) {
                val a = amplitude()
                if (a > 0.001f && size.width > 0f && size.height > 0f) {
                    shader.update(size.width, size.height, a, shape, axis)
                    shader.renderEffect()
                } else {
                    null
                }
            } else {
                null
            }
        }
        .drawWithContent {
            // 不可见那一面连内容带效果一起不画，省掉半程开销
            if (visible()) drawContent()
        }
}
