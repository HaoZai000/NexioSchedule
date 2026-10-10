// 路径模糊阴影 —— 跨平台绘制（SearchBar 等毛玻璃容器的悬浮投影）
//
// 与 PopupShadow 的区别：那个是「圆角矩形描边」的模糊（几何参数描述），
// 这个是「任意路径填充」的模糊（形状来自 Shape.createOutline，可能是超椭圆等
// 自定义 Path，几何参数表达不了）——所以 expect 直接收 compose Path。
//
// 分工：
//   androidMain —— BlurMaskFilter + nativeCanvas（从 SearchBar 原实现逐字搬出，零回归）
//   skikoMain   —— skiko 的 MaskFilter.MakeBlur + nativeCanvas（Skia 同源 API，语义一致）

package top.yukonga.miuix.kmp.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 在当前绘制范围里，按给定路径画一块**模糊填充阴影**。
 *
 * @param path        阴影形状（填充语义，不做描边）
 * @param color       阴影颜色（含 alpha）
 * @param blurRadius  模糊半径（px）
 */
expect fun DrawScope.drawBlurredPathShadow(
    path: Path,
    color: Color,
    blurRadius: Float,
)
