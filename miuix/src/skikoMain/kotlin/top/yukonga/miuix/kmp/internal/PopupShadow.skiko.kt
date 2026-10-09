package top.yukonga.miuix.kmp.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Skia（iOS / Desktop / wasm）实现 —— **尚未实现，见下**。
 *
 * 为什么留空：SkSL 版的模糊描边需要在 Skia 侧把「环形路径」喂给 RuntimeEffect，
 * 实现方式可参照 backdrop 的 skikoMain（ImageFilter.makeRuntimeShader + SDF）。
 * 但 iOS target 在 Windows 上无法编译验证（无 macOS / 无 .konan），
 * 与其提交一段无法验证的盲代码，不如把 expect 留在编译期暴露出来，
 * 等真正加 iOS target 时由能跑 macOS 的人补齐 —— 那时会在编译期立刻报错，不会漏。
 */
actual fun DrawScope.drawBlurredRingShadow(
    size: Size,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
    spread: Float,
    blurRadius: Float,
    color: Color,
) {
    // TODO(iOS): 用 SkSL 模糊描边实现，或降级为无模糊的半透明环形填充。
    // 降级路径与 backdrop 的 isRenderEffectSupported() 守卫同一思路：
    // 拿不到模糊能力时，宁可少一层阴影，也不要整个面板不画。
}
