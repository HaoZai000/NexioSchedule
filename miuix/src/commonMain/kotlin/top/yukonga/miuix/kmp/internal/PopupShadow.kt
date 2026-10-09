// 面板投影阴影 —— 跨平台绘制
//
// 背景：Miuix fork 的弹窗投影原本用 android.graphics 的 BlurMaskFilter + nativeCanvas 手绘，
// 这把整个 ListPopup.kt 钉死在 Android（Kotlin/Native 上根本编不过）。
// 现在下沉到 expect/actual：
//   androidMain —— 仍走 BlurMaskFilter（与迁移前逐像素一致，零回归）
//   skikoMain   —— 走 SkSL 模糊描边（backdrop 的 skikoMain 已有同套路范例）
//
// 形状语义：环形路径（外圈 CW 减内圈 CCW）再整体模糊 = 圆角矩形描边的模糊。
// 为什么不用 backdrop 的 shadow：它的 ShadowModifier 走 Paint().blur()，
// 语义是「填充形状后模糊」，而这里要的是「模糊描边」，两者不是一回事。

package top.yukonga.miuix.kmp.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 在当前绘制范围里，画一块圆角矩形的模糊投影。
 *
 * @param size       绘制区域尺寸
 * @param left/top   目标圆角矩形左上角（画布坐标）
 * @param width/height 目标圆角矩形尺寸
 * @param radius     圆角半径（px）
 * @param spread     投影外扩距离（px），0 表示不外扩
 * @param blurRadius 模糊半径（px）
 * @param color      阴影颜色
 */
expect fun DrawScope.drawBlurredRingShadow(
    size: Size,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
    spread: Float,
    blurRadius: Float,
    color: Color,
)
