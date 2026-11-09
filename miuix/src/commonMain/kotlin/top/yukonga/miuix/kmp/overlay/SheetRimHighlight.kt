package top.yukonga.miuix.kmp.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.softglass.platform.asComposeShader
import com.haooz.softglass.platform.createGlassRuntimeShader
import com.haooz.softglass.platform.isRuntimeShaderSupported

/**
 * 只画「边缘高光」的 AGSL：不采样、不模糊、不录屏。
 *
 * 光向固定从上往下（屏幕坐标 y 向下，所以指向光源是 `(0, -1)`）。每个像素取
 * 圆角矩形 SDF 的法线，`dot(normal, light)` 就是从 1（顶边）连续降到 0（左右直边）
 */
private const val SHEET_RIM_SHADER = """
uniform float2 uSize;
uniform float uRadius;
uniform float uEdgePx;
uniform float uTopAlpha;
uniform float uBottomAlpha;
uniform float2 uLight;
uniform float uFlat;
uniform float uCornerScale;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 normalAt(float2 p, float2 b, float r) {
    float e = 0.75;
    float dx = sdRoundRect(p + float2(e, 0.0), b, r) - sdRoundRect(p - float2(e, 0.0), b, r);
    float dy = sdRoundRect(p + float2(0.0, e), b, r) - sdRoundRect(p - float2(0.0, e), b, r);
    float2 g = float2(dx, dy);
    float len = length(g);
    return len > 1e-5 ? g / len : float2(0.0, -1.0);
}

half4 main(float2 fragCoord) {
    float2 hs = uSize * 0.5;
    float2 p = fragCoord - hs;
    float sd = sdRoundRect(p, hs, uRadius);
    if (sd > 0.0) {
        return half4(0.0);
    }
    float d = -sd;
    float2 n = normalAt(p, hs, uRadius);
    float ndl = dot(n, normalize(uLight));
    // 圆角段单独一档粗细：直边上的等距线正对像素网格，1px 就是 1px；
    // 圆角上的等距弧斜切像素，抗锯齿会把它摊成 2~3px，肉眼就是「转角更粗」。
    // cornerness 在角部平滑升到 1（不产生新的接缝），带宽收敛到 uCornerScale 倍。
    float2 qc = abs(p) - (hs - uRadius);
    float cornerness = smoothstep(0.0, uRadius * 0.55, min(qc.x, qc.y));
    float edge = uEdgePx * mix(1.0, uCornerScale, cornerness);
    float t = clamp(d / max(edge, 0.001), 0.0, 1.0);
    float f = 1.0 - smoothstep(uFlat, 1.0, t);
    float fromTop = p.y + hs.y;
    float fromBottom = hs.y - p.y;
    float topAmt = 1.0 - smoothstep(uRadius * 0.35, uRadius * 1.55, fromTop);
    float bottomAmt = clamp(-ndl, 0.0, 1.0) * (1.0 - smoothstep(0.0, uRadius * 1.3, fromBottom));
    float amount = topAmt * uTopAlpha + bottomAmt * uBottomAlpha;
    float a = clamp(f * amount, 0.0, 1.0);
    return half4(half3(1.0) * half(a), half(a));
}
"""

/**
 * 贴底 / 居中弹窗的上下方向高光描边。
 *
 * 直接当 Paint shader 画在最上层（[ShaderBrush]），不建离屏层、不采样页面，
 * 与整套柔光玻璃完全解耦 —— 弹窗底色该是什么还是什么。
 */
@Composable
internal fun Modifier.sheetRimHighlight(isDark: Boolean, cornerRadius: Dp): Modifier {
    if (!isRuntimeShaderSupported()) return this
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    // 浅色面板 #F2F2F2 与纯白只差 5%，线宽必须加上去才看得见
    val edgePx = with(density) { 2.2.dp.toPx() }
    // 深色面板上压一档避免发灰；浅色面板 #F2F2F2 与纯白的差本来就小，
    // alpha 必须顶到 1 才看得见描边。
    val flat = if (isDark) 0f else 0.45f
    // 圆弧段的独立粗细系数（相对直边）
    val cornerScale = 1f
    val topAlpha = if (isDark) 0.28f else 1f
    val bottomAlpha = if (isDark) 0.09f else 0.55f
    val shader = remember(radiusPx, edgePx, topAlpha, bottomAlpha, flat, cornerScale) {
        createGlassRuntimeShader(SHEET_RIM_SHADER).apply {
            setFloatUniform("uRadius", radiusPx)
            setFloatUniform("uEdgePx", edgePx)
            setFloatUniform("uTopAlpha", topAlpha)
            setFloatUniform("uBottomAlpha", bottomAlpha)
            // 光正从上往下：左右直边 dot 为 0，转角处连续衰减
            setFloatUniform("uLight", 0f, -1f)
            setFloatUniform("uFlat", flat)
            setFloatUniform("uCornerScale", cornerScale)
        }
    }
    val brush = remember(shader) { ShaderBrush(shader.asComposeShader()) }
    return this.drawWithContent {
        drawContent()
        shader.setFloatUniform("uSize", size.width, size.height)
        drawRect(brush = brush)
    }
}
