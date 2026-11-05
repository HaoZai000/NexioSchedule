// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.haooz.chedule.ui.glass.SoftGlassBackdrop
import com.haooz.chedule.ui.glass.SoftGlassStyle
import com.haooz.chedule.ui.glass.isSoftGlassShaderAvailable
import com.haooz.chedule.ui.glass.prepareSoftGlassBackdrop
import com.haooz.chedule.ui.glass.softGlass
import com.haooz.chedule.ui.glass.wallpaperTexMap

/**
 * 柔光玻璃的绘制能力，供 `commonMain` 的卡片调用。
 *
 * 需要这层抽象：卡片背景由平台画（AGSL / SkSL + 纹理采样），`commonMain` 引不到 `RuntimeShader`，
 * 所以这里只暴露「给我一个 Modifier」。shader 实例由 `SoftGlassProgramCache` 按尺寸 / 圆角共享。
 */
interface SoftGlassSurface {
    /**
     * @param origin 面板左上角的**窗口坐标**，绘制期求值。
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
 * 只准备「壁纸纹理 + 坐标映射」，不做任何模糊 / 调色烘焙：
 * 拖模糊、拖壁纸、改任何外观参数都只重灌 uniform。无壁纸 / 平台没有 shader 时为 null，
 * 所有面板退回纯色表面。
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
 * 准备玻璃的采样源。
 *
 * ⚠ 只有 [wallpaperBitmap] 会触发重建（降采样 + 缩略图）。样式、缩放、偏移一律**不进任何
 * remember key**：它们分别只是 shader 的 uniform 与一个 2×2 仿射，改成 State 让绘制期去读，
 * 这样拖模糊 / 拖壁纸连卡片 modifier 都不会重建，更不会重烘位图。
 *
 * @param originTransform 绘制期求值的「窗口坐标 → 壁纸层逻辑坐标」修正
 */
@Composable
fun rememberSoftGlassSurface(
    wallpaperBitmap: ImageBitmap?,
    wallpaperScale: Float,
    wallpaperOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    style: SoftGlassStyle = SoftGlassStyle(),
    originTransform: (Offset) -> Offset = { it },
): SoftGlassSurface? {
    val backdrop = remember(wallpaperBitmap) {
        val bmp = wallpaperBitmap
        if (bmp == null || !isSoftGlassShaderAvailable()) {
            null
        } else {
            prepareSoftGlassBackdrop(bmp)
        }
    } ?: return null

    val styleState = remember { mutableStateOf(style) }
    SideEffect { styleState.value = style }

    val texMap = remember(backdrop, wallpaperScale, wallpaperOffset, screenWidthPx, screenHeightPx) {
        wallpaperTexMap(
            textureWidth = backdrop.levels[0].width,
            textureHeight = backdrop.levels[0].height,
            wallpaperScale = wallpaperScale,
            wallpaperOffset = wallpaperOffset,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    }
    // 映射做成 State：拖壁纸时只让绘制失效，不重建 surface，也不重建每张卡的 modifier。
    val texMapState = remember { mutableStateOf(texMap) }
    SideEffect { texMapState.value = texMap }

    return remember(backdrop, originTransform) {
        DefaultSoftGlassSurface(backdrop, styleState, texMapState, originTransform)
    }
}

private class DefaultSoftGlassSurface(
    private val backdrop: SoftGlassBackdrop,
    private val styleState: State<SoftGlassStyle>,
    private val texMapState: State<FloatArray>,
    private val originTransform: (Offset) -> Offset,
) : SoftGlassSurface {
    override fun modifier(
        density: Density,
        origin: () -> Offset,
        radiusPx: Float?,
        redraw: () -> Unit,
    ): Modifier = Modifier.softGlass(
        backdrop = backdrop,
        styleProvider = { styleState.value },
        density = density,
        // positionInWindow() 含祖先 graphicsLayer 变换，先还原到壁纸层的逻辑坐标系
        origin = { originTransform(origin()) },
        texMapProvider = { texMapState.value },
        radiusPx = radiusPx,
        redraw = redraw,
    )
}
