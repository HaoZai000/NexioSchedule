// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.haooz.chedule.ui.glass.SoftGlassBackdrop
import com.haooz.chedule.ui.glass.SoftGlassStyle
import com.haooz.chedule.ui.glass.buildSoftGlassBackdrop
import com.haooz.chedule.ui.glass.isSoftGlassShaderAvailable
import com.haooz.chedule.ui.glass.softGlass

/**
 * 柔光玻璃的绘制能力，供 `commonMain` 的卡片调用。
 *
 * 需要这层抽象：卡片背景由平台画（AGSL / SkSL + 位图采样），`commonMain` 引不到 `RuntimeShader`，
 * 所以这里只暴露「给我一个 Modifier」。shader 实例由 `SoftGlassProgramCache` 按尺寸 / 圆角 / 样式共享。
 */
interface SoftGlassSurface {
    /**
     * @param origin 面板左上角在背景位图里的坐标，**绘制期**求值。
     *   位图已与屏幕对齐（同尺寸同原点），所以直接传窗口坐标即可。
     * @param radiusPx 圆角（px）；null = 用样式里的 `cornerRadius`。
     * @param redraw 绘制期读一次的「位置信号」，让滚动 / 翻页时按帧失效。
     */
    fun modifier(
        density: Density,
        origin: () -> Offset,
        radiusPx: Float?,
        redraw: () -> Unit = {},
    ): Modifier
}

/**
 * 页面级柔光玻璃能力，按页共享（课表、今日各一份，页面样式互不影响）。
 *
 * 对齐 + 模糊是同步烘焙，同一页只算一次；由 `CourseScheduleAppMainContent` 计算并下发。
 * 无壁纸 / 平台没有 shader 时为 null，所有面板退回纯色表面。
 */
val LocalSoftGlassSurface = staticCompositionLocalOf<SoftGlassSurface?> { null }

/**
 * 课表滚动 / 翻页的位置信号。
 *
 * 卡片的玻璃是在**卡片自己的 draw 里**画的，而 `verticalScroll` 与 `HorizontalPager`
 * 都用 `placeWithLayer` 平移内容：子树只是被整体搬走，不会重新绘制。不主动失效的话，
 * 玻璃纹理就跟着卡片一起平移（看起来像贴纸），松手/落页布局再次派发时才更新一帧。
 *
 * 在 `onDrawBehind` 里读这些 State，等于把这张卡的绘制订阅到滚动位置上：
 * 滚动 / 翻页 / 回弹每变一个像素就按帧重画，`uOrigin` 才能跟手。
 */
val LocalGlassRedrawSignal = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 把壁纸按它**实际显示**的变换（ContentScale.Fit 居中 × 手势缩放 × 用户偏移）
 * 对齐成半分辨率基准位图，再出轻 / 重两级模糊。
 *
 * 对齐在内部消化，调用方不用管壁纸缩放；shader 用 `bakeScale` 把面板坐标折算到纹理。
 * 同步烘焙，第一帧就能带着玻璃出。
 */
@Composable
fun rememberSoftGlassSurface(
    wallpaperBitmap: ImageBitmap?,
    wallpaperScale: Float,
    wallpaperOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    style: SoftGlassStyle = SoftGlassStyle(),
): SoftGlassSurface? {
    val density = LocalDensity.current
    // 同步烘焙（半分辨率 + 低分辨率模糊）单次约几十毫秒，第一帧就能带着玻璃出。
    // key 必须是整个 style：调色已跑在烘焙里，任一颜色参数变化都得重烘，不能只盯两个模糊半径。
    val backdrop = remember(
        wallpaperBitmap,
        wallpaperScale,
        wallpaperOffset,
        screenWidthPx,
        screenHeightPx,
        density.density,
        style,
    ) {
        val bmp = wallpaperBitmap
        if (bmp == null || bmp.width <= 0 || bmp.height <= 0 || !isSoftGlassShaderAvailable()) {
            null
        } else {
            runCatching {
                buildSoftGlassBackdrop(
                    wallpaper = bmp,
                    style = style,
                    density = density.density,
                    scale = wallpaperScale,
                    offset = wallpaperOffset,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                )
            }.getOrNull()
        }
    }
    if (backdrop == null) return null
    return remember(backdrop, style) { DefaultSoftGlassSurface(backdrop, style) }
}

private class DefaultSoftGlassSurface(
    private val backdrop: SoftGlassBackdrop,
    private val style: SoftGlassStyle,
) : SoftGlassSurface {
    override fun modifier(
        density: Density,
        origin: () -> Offset,
        radiusPx: Float?,
        redraw: () -> Unit,
    ): Modifier = Modifier.softGlass(
        backdrop = backdrop,
        style = style,
        density = density,
        origin = origin,
        radiusPx = radiusPx,
        redraw = redraw,
    )
}
