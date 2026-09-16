package com.kyant.backdrop.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.unit.Density
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class InverseLayerScope : GraphicsLayerScope {

    override var size: Size = Size.Unspecified
    override var density: Float = 1f
    override var fontScale: Float = 1f

    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 0f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var ambientShadowColor: Color = DefaultShadowColor
    override var spotShadowColor: Color = DefaultShadowColor
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = DefaultCameraDistance
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var renderEffect: RenderEffect? = null
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var colorFilter: ColorFilter? = null
    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto

    private var matrix: Matrix? = null

    fun DrawTransform.inverseTransform(
        density: Density,
        layerBlock: GraphicsLayerScope.() -> Unit
    ) {
        this@InverseLayerScope.size = size
        this@InverseLayerScope.density = density.density
        fontScale = density.fontScale

        layerBlock()

        // graphicsLayer 默认 TransformOrigin.Center：反变换必须绕同一 pivot，
        // 否则正向中心放大 + 反向左上缩小会让采样内容向左上「缩水」。
        inverseTransformAtPivot(
            rotationZ = rotationZ,
            scaleX = scaleX,
            scaleY = scaleY,
            transformOrigin = transformOrigin,
            width = size.width,
            height = size.height
        )
    }

    fun reset() {
        size = Size.Unspecified
        density = 1f
        fontScale = 1f

        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        translationX = 0f
        translationY = 0f
        shadowElevation = 0f
        ambientShadowColor = DefaultShadowColor
        spotShadowColor = DefaultShadowColor
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        cameraDistance = DefaultCameraDistance
        transformOrigin = TransformOrigin.Center
        shape = RectangleShape
        clip = false
        renderEffect = null
        blendMode = BlendMode.SrcOver
        colorFilter = null
        compositingStrategy = CompositingStrategy.Auto

        matrix = null
    }

    private fun DrawTransform.inverseTransformAtPivot(
        rotationZ: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        transformOrigin: TransformOrigin = TransformOrigin.Center,
        width: Float,
        height: Float
    ) {
        val pivot = Offset(
            transformOrigin.pivotFractionX * width,
            transformOrigin.pivotFractionY * height
        )

        if (rotationZ == 0f) {
            if (scaleX != 0f && scaleY != 0f) {
                scale(1f / scaleX, 1f / scaleY, pivot)
            }
            return
        }

        val matrix = matrix ?: Matrix().also { matrix = it }
        if (matrix.values.size < 16) return

        // 先平移到 pivot，再做旋转+缩放的逆，再平移回去
        val rz = rotationZ * (PI / 180.0)
        val rsz = sin(rz).toFloat()
        val rcz = cos(rz).toFloat()

        val a00 = rcz * scaleX
        val a01 = rsz * scaleY
        val a10 = -rsz * scaleX
        val a11 = rcz * scaleY

        val det = a00 * a11 - a01 * a10
        if (det == 0f) return
        val invDet = 1f / det
        // M = T(pivot) * Inv(R*S) * T(-pivot)
        val m00 = a11 * invDet
        val m01 = -a01 * invDet
        val m10 = -a10 * invDet
        val m11 = a00 * invDet
        val m02 = pivot.x - (m00 * pivot.x + m01 * pivot.y)
        val m12 = pivot.y - (m10 * pivot.x + m11 * pivot.y)

        // Compose Matrix：平移在 [row, 3]，先置单位阵再写线性部分与平移
        for (i in 0..3) {
            for (j in 0..3) {
                matrix[i, j] = if (i == j) 1f else 0f
            }
        }
        matrix[0, 0] = m00
        matrix[0, 1] = m01
        matrix[1, 0] = m10
        matrix[1, 1] = m11
        matrix[0, 3] = m02
        matrix[1, 3] = m12

        transform(matrix)
    }
}
