package com.haooz.chedule.ui.utils

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.haooz.softglass.platform.isRenderEffectSupported

/** 快照的源区域：指定了 [TransitionLayerSnapshot.bounds] 用 bounds，否则取整层尺寸。 */
private fun sourceBoundsOf(snapshot: TransitionLayerSnapshot): Rect =
    snapshot.bounds ?: Rect(
        0f,
        0f,
        snapshot.layer.size.width.toFloat(),
        snapshot.layer.size.height.toFloat(),
    )

/** 有光栅化副本就画它，否则重放源图层。 */
private fun DrawScope.drawSnapshot(snapshot: TransitionLayerSnapshot) {
    val frozen = snapshot.frozen
    if (frozen != null) drawImage(frozen) else drawLayer(snapshot.layer)
}

/**
 * 保留的绘制图层，可限定到其局部像素坐标中的一块区域。
 *
 * 裁剪用 `GraphicsLayer` + `clipRect`。`toImageBitmap()` 在 Android 上
 * 返回硬件位图，喂给软件 canvas 会抛 `Software rendering doesn't support hardware bitmaps`。
 */
class TransitionLayerSnapshot(
    val layer: GraphicsLayer,
    val bounds: Rect? = null,
) {
    private val frozenState = mutableStateOf<ImageBitmap?>(null)

    /**
     * 源图层的光栅化副本（整层尺寸，裁剪仍按 [bounds] 在绘制期做）。
     *
     * `drawLayer` 是活引用：每帧重放整页绘制列表（镜像平铺会重复重放多次）。副本是
     * 单张纹理，适合源内容固定、需要整屏反复绘制的转场背景。也顺手解决源内容在
     * 形变期间被隐藏后重放内容跟着消失的问题。
     * ⚠ Android 上是硬件位图，只能用于上屏绘制；走 state 写入，只失效绘制不触发重组。
     */
    var frozen: ImageBitmap?
        get() = frozenState.value
        set(value) {
            frozenState.value = value
        }
}

/**
 * 把 [layer] 光栅化后原地替换 [frozen]，失败则保留活引用重放。
 *
 * 整页快照的光栅化有一次性回读开销，必须在形变开始前调用，别塞进动画帧里。
 */
suspend fun TransitionLayerSnapshot.freezeSource() {
    if (frozen != null) return
    frozen = runCatching { layer.toImageBitmap() }.getOrNull()
}

/** 按 [contentScale] / [alignment] 布局快照，不分配、不光栅化位图。 */
@Composable
fun TransitionLayerImage(
    snapshot: TransitionLayerSnapshot,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.TopCenter,
) {
    val painter = remember(snapshot, snapshot.layer.size) {
        object : Painter() {
            // 源区域只算一次，避免每帧分配 Rect
            private val source: Rect = sourceBoundsOf(snapshot)

            override val intrinsicSize: Size
                get() = source.size.takeIf { it.width > 0f && it.height > 0f } ?: Size.Unspecified

            override fun DrawScope.onDraw() {
                if (source.width <= 0f || source.height <= 0f) return
                clipRect {
                    scale(size.width / source.width, size.height / source.height, pivot = Offset.Zero) {
                        translate(-source.left, -source.top) { drawSnapshot(snapshot) }
                    }
                }
            }
        }
    }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.clipToBounds(),
        contentScale = contentScale,
        alignment = alignment,
    )
}

/**
 * 用整页快照充当形变期间的下层背景：按 [shrink] 居中缩小，四周露出部分用快照镜像
 * 补齐（3×3 平铺、四角双向翻转），因此不会露出下面的实时页面。
 *
 * [shrink] / [blurRadiusPx] 只在绘制阶段读取，不触发组合或布局。
 */
@Composable
fun MirroredSnapshotBackdrop(
    snapshot: TransitionLayerSnapshot,
    shrink: () -> Float,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    blurRadiusPx: () -> Float = { 0f },
) {
    val effectSupported = remember { isRenderEffectSupported() }
    // 源区域只算一次，绘制阶段不再分配
    val source = remember(snapshot) { sourceBoundsOf(snapshot) }
    // 整屏模糊与 3×3 平铺都是按像素填充的操作：离屏缓冲边长减半后 GPU 成本约降到 1/4，
    // 模糊本身会掩盖降采样差异。没有渲染效果时不需要模糊，也不降采样。
    val resolution = if (effectSupported) 0.5f else 1f

    Box(modifier.fillMaxSize().clipToBounds()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize(resolution)
                .graphicsLayer {
                    scaleX = 1f / resolution
                    scaleY = 1f / resolution
                    transformOrigin = TransformOrigin(0f, 0f)
                    // 半径按缓冲尺寸折算，放大后视觉半径与整屏一致
                    val radius = blurRadiusPx() * resolution
                    renderEffect = if (radius > 0.01f && effectSupported) {
                        BlurEffect(null, radius, radius, TileMode.Clamp)
                    } else {
                        null
                    }
                }
        ) {
            drawRect(backgroundColor)
            if (source.width <= 0f || source.height <= 0f) return@Canvas

            val factor = shrink().coerceIn(0f, 1f)
            val width = size.width * factor
            val height = size.height * factor
            val left = (size.width - width) * 0.5f
            val top = (size.height - height) * 0.5f
            val right = left + width
            val bottom = top + height
            // 图层像素 → 容器像素的基准比例
            val baseX = size.width / source.width
            val baseY = size.height / source.height

            for (xi in -1..1) {
                val clipLeft = when (xi) {
                    -1 -> 0f
                    0 -> left
                    else -> right
                }
                val clipRight = when (xi) {
                    -1 -> left
                    0 -> right
                    else -> size.width
                }
                if (clipRight <= clipLeft) continue

                for (yi in -1..1) {
                    val clipTop = when (yi) {
                        -1 -> 0f
                        0 -> top
                        else -> bottom
                    }
                    val clipBottom = when (yi) {
                        -1 -> top
                        0 -> bottom
                        else -> size.height
                    }
                    if (clipBottom <= clipTop) continue

                    val scaleX = (if (xi == 0) 1f else -1f) * factor * baseX
                    val scaleY = (if (yi == 0) 1f else -1f) * factor * baseY
                    val destX = if (xi > 0) right + width else left
                    val destY = if (yi > 0) bottom + height else top

                    clipRect(clipLeft, clipTop, clipRight, clipBottom) {
                        translate(destX, destY) {
                            scale(scaleX, scaleY, pivot = Offset.Zero) {
                                translate(-source.left, -source.top) { drawSnapshot(snapshot) }
                            }
                        }
                    }
                }
            }
        }
    }
}
