// Copyright (C) 2026 Haooz · AGPL-3.0-only

package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.SoftGlassBackdrop
import com.haooz.softglass.glass.SoftGlassStyle
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.glass.glassBlurRadiusForSigma
import com.haooz.softglass.glass.prepareSoftGlassBackdrop
import com.haooz.softglass.glass.softGlass
import com.haooz.softglass.glass.wallpaperTexMap
import com.haooz.softglass.platform.asSoftwareBitmap
import com.haooz.softglass.platform.isSoftGlassShaderAvailable
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 玻璃控件的拖动手感常量。值统一定义在 miuix（玻璃档位），这里只做本模块别名；
 * 按钮 / 菜单 / 底栏都从 `com.haooz.chedule.ui.components` 引用，不能删。
 */
internal const val GlassDragStretchRatio = top.yukonga.miuix.kmp.interactive.GlassDragStretchRatio
internal const val GlassDragTranslationDamping = top.yukonga.miuix.kmp.interactive.GlassDragTranslationDamping

/**
 * 柔光玻璃的绘制能力，供 `commonMain` 的卡片调用。
 *
 * 需要这层抽象：卡片背景由平台画（AGSL / SkSL + 纹理采样），`commonMain` 引不到 `GlassRuntimeShader`，
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
 * 实时录制版柔光：采样控件脚下的**真实内容**，能录到已经在屏幕上的课程卡片。
 *
 * 与 [SoftGlassSurface] 的差别只有采样源：网格卡片采静态壁纸（更省、跨页可复用），
 * 浮层必须采「它下面此刻画了什么」，否则浮层一拖起来就成了纯色块。
 * 样式由调用方传入，两路共用同一份 [SoftGlassStyle]，参数不会漂移。
 *
 * @param shape 可见轮廓；实时路径靠它裁出玻璃形状（静态路径由 shader 自己处理圆角）。
 * @param radiusPx 圆角（px），只用于倒角带的光学距离。
 * @param redraw 绘制期读一次的位置信号，让 `graphicsLayer` 平移中的浮层逐帧重采。
 * @param blurSigmaPx 模糊量口径（屏幕 px）。
 */
@Composable
fun Modifier.softGlassBackdrop(
    backdrop: GlassBackdrop,
    shape: Shape,
    style: SoftGlassStyle,
    radiusPx: Float,
    redraw: () -> Unit = {},
    blurSigmaPx: Float? = null,
): Modifier {
    val styleState = rememberUpdatedState(style)
    val redrawState = rememberUpdatedState(redraw)
    val blurSigmaState = rememberUpdatedState(blurSigmaPx)
    // lambda 引用必须稳定：drawBackdrop 按引用比较 shape，每次重组新建会让节点重录采样层
    val shapeProvider: () -> Shape = remember(shape) { { shape } }
    val effects: GlassEffectScope.() -> Unit = remember(radiusPx) {
        {
            redrawState.value()
            val s = styleState.value
            // Mirror 让边缘不退化成一圈硬边。σ 口径（浮层对齐卡片）要先换回 BlurEffect 半径
            val sigma = blurSigmaState.value
            blur(
                if (sigma != null) glassBlurRadiusForSigma(sigma) else s.smallBlurRadius,
                TileMode.Mirror,
            )
            padding = 0f
            runtimeShaderEffect(
                // key 只认字符串：尺寸不同必须分开，否则同帧多张卡的 uniform 会互相覆盖
                key = "NexioSoftGlassLiveCourseCard_" +
                    "${size.width.toInt()}x${size.height.toInt()}",
                shaderString = SOFT_GLASS_LIVE_SHADER,
                uniformShaderName = "content",
            ) {
                applySoftGlass(
                    style = s,
                    width = size.width,
                    height = size.height,
                    radiusPx = radiusPx,
                )
            }
        }
    }
    return drawBackdrop(
        backdrop = backdrop,
        shadow = null,
        shape = shapeProvider,
        effects = effects,
        // 网格卡片那条静态路径没有投影，浮层要当同一块材质就不能自带一层默认阴影
        onDrawSurface = null,
    )
}


/** 壁纸亮度的采样源重建延迟：亮度滑杆拖动中只跟手壁纸层，停手后卡片再补一帧。 */
private const val WALLPAPER_BRIGHTNESS_SETTLE_MS = 120L

/** 采样源最长边（与 `prepareSoftGlassBackdrop` 的 BASE_MAX_EDGE 一致），先缩小再调色省一次全尺寸拷贝。 */
private const val GLASS_SOURCE_MAX_EDGE = 1024

/**
 * 准备玻璃的采样源。
 *
 * ⚠ 只有 [wallpaperBitmap] 与 [wallpaperBrightness] 会触发重建（降采样 + 缩略图）。样式、缩放、
 * 偏移一律**不进任何 remember key**：它们分别只是 shader 的 uniform 与一个 2×2 仿射，改成 State
 * 让绘制期去读，这样拖模糊 / 拖壁纸连卡片 modifier 都不会重建，更不会重烘位图。
 *
 * 壁纸亮度是用 ColorFilter 画出来的，而玻璃采的是原图；这里先把同一档亮度烘进采样源，
 * 否则卡片色调永远和壁纸差一档。拖动滑杆时不重建（每帧烘一座金字塔会卡），停手后再更新。
 *
 * @param wallpaperBrightness 已生效的壁纸亮度（-50..50），与壁纸层 `ColorFilter` 同口径
 * @param originTransform 绘制期求值的「窗口坐标 → 壁纸层逻辑坐标」修正
 */
@Composable
fun rememberSoftGlassSurface(
    wallpaperBitmap: ImageBitmap?,
    wallpaperScale: Float,
    wallpaperOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    wallpaperBrightness: Float = 0f,
    style: SoftGlassStyle = SoftGlassStyle(),
    originTransform: (Offset) -> Offset = { it },
): SoftGlassSurface? {
    val sourceBitmap = wallpaperBitmap
    if (sourceBitmap == null || !isSoftGlassShaderAvailable()) return null

    // 亮度拖动中不停重烘金字塔会掉帧：先跟手壁纸层，停手后再生效。
    var settledBrightness by remember(sourceBitmap) { mutableStateOf(wallpaperBrightness) }
    LaunchedEffect(sourceBitmap, wallpaperBrightness) {
        if (settledBrightness == wallpaperBrightness) return@LaunchedEffect
        delay(WALLPAPER_BRIGHTNESS_SETTLE_MS)
        settledBrightness = wallpaperBrightness
    }
    val backdropBitmap = remember(sourceBitmap, settledBrightness) {
        tintWallpaperForGlass(sourceBitmap, settledBrightness)
    }
    val backdrop = remember(backdropBitmap) {
        prepareSoftGlassBackdrop(backdropBitmap)
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

/**
 * 把壁纸亮度烘进玻璃采样源。
 *
 * 亮度为 0 时直接复用原图；否则先缩到采样源分辨率再带上与壁纸层完全一致的
 * 乘性 ColorFilter，避免多拷一份原尺寸位图。
 */
private fun tintWallpaperForGlass(bitmap: ImageBitmap, brightness: Float): ImageBitmap {
    if (brightness == 0f) return bitmap
    val gain = (1f + brightness / 50f).coerceIn(0f, 2f)
    if (gain == 1f) return bitmap

    // 硬件位图不能画进软件 Canvas
    val source = bitmap.asSoftwareBitmap()
    val longest = max(source.width, source.height)
    val scale =
        if (longest > GLASS_SOURCE_MAX_EDGE) GLASS_SOURCE_MAX_EDGE.toFloat() / longest else 1f
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)

    val out = ImageBitmap(width, height, ImageBitmapConfig.Argb8888)
    Canvas(out).drawImageRect(
        image = source,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(source.width, source.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(width, height),
        paint = Paint().apply {
            filterQuality = FilterQuality.Medium
            colorFilter = ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        gain, 0f, 0f, 0f, 0f,
                        0f, gain, 0f, 0f, 0f,
                        0f, 0f, gain, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
        },
    )
    return out
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
