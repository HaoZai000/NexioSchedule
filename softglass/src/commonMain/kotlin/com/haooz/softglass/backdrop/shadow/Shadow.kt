package com.haooz.softglass.backdrop.shadow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * 玻璃表面外侧的柔和投影。
 *
 * 由库自己画（离屏层 + 模糊描边挖空），不用平台阴影，三端像素一致。
 * [radius] 是模糊半径，[offset] 决定落点，形状始终跟随玻璃本身的轮廓。
 */
@Immutable
data class Shadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, radius / 6f),
    val color: Color = Color.Black.copy(alpha = 0.1f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {

    companion object {

        @Stable
        val Default: Shadow = Shadow()
    }
}
