package com.haooz.softglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.haooz.softglass.backdrop.backdrops.emptyBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.shadow.Shadow
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 投影必须真的落在形状外：不传就开，传 null 就关。
 *
 * 离屏渲染读像素 —— 参数对但画面没画出来同样是回归。
 */
class ShadowRenderTest {

    /** 返回 [形状上方外侧, 远处空白, 形状内部] 三点的红通道。 */
    private fun render(surface: Modifier): IntArray {
        val scene = ImageComposeScene(width = 200, height = 200, density = Density(1f)) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Box(surface.size(80.dp).background(Color.Red))
            }
        }
        val bitmap = Bitmap.makeFromImage(scene.render())
        // 方块居中 (60,60)-(140,140)：上方 15px 处应吃到投影，右下远处仍是白底
        val result = intArrayOf(
            red(bitmap.getColor(100, 45)),
            red(bitmap.getColor(190, 190)),
            red(bitmap.getColor(100, 100)),
        )
        scene.close()
        return result
    }

    private fun glassSurface(shadow: (() -> Shadow?)? = null, omitShadow: Boolean = false): Modifier {
        val base = Modifier
        return if (omitShadow) {
            base.drawBackdrop(
                backdrop = emptyBackdrop(),
                shape = { ContinuousRoundedRectangle(20.dp) },
                effects = {},
            )
        } else {
            base.drawBackdrop(
                backdrop = emptyBackdrop(),
                shape = { ContinuousRoundedRectangle(20.dp) },
                effects = {},
                shadow = shadow,
            )
        }
    }

    @Test
    fun omittedShadowDefaultsToOn() {
        val (outsideTop, far, inside) = render(glassSurface(omitShadow = true))
        assertTrue(outsideTop < 255, "不传 shadow 时应默认画投影，实际 R=$outsideTop")
        assertEquals(255, far, "远离形状处必须还是纯白")
        assertEquals(255, inside, "形状内部的红色方块 R 应为 255")
    }

    @Test
    fun explicitShadowIsPainted() {
        val (outsideTop, _, _) = render(glassSurface(shadow = { Shadow(alpha = 0.3f) }))
        assertTrue(outsideTop < 255, "显式投影应可见，实际 R=$outsideTop")
    }

    @Test
    fun nullShadowLeavesSurfaceClean() {
        val (outsideTop, _, _) = render(glassSurface(shadow = null))
        assertEquals(255, outsideTop, "传 null 后形状外应恢复纯白，实际 R=$outsideTop")
    }

    private fun red(argb: Int): Int = (argb shr 16) and 0xFF
}
