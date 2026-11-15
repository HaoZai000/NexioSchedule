@file:SuppressLint("NewApi")

package com.haooz.chedule.ui.effects.motion

import android.annotation.SuppressLint
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/** null = 还没探测。探测会顺便编译一次 AGSL，编译失败也视为不支持。 */
private var arcBendCapable: Boolean? = null

actual fun arcBendSupported(): Boolean {
    if (arcBendCapable == null) {
        arcBendCapable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            false
        } else {
            runCatching { RuntimeShader(ARC_BEND_SHADER) }
                .onFailure { Log.w("ArcBend", "AGSL 编译失败：调用方应降级", it) }
                .isSuccess
        }
    }
    return arcBendCapable == true
}

internal actual fun createArcBendShader(): ArcBendShader? {
    if (!arcBendSupported()) return null
    val native = runCatching { RuntimeShader(ARC_BEND_SHADER) }.getOrElse {
        Log.w("ArcBend", "AGSL 实例创建失败", it)
        return null
    }
    return object : ArcBendShader {
        override fun update(
            width: Float,
            height: Float,
            amplitude: Float,
            shape: ArcBendShape,
            axis: ArcBendAxis,
        ) {
            native.setFloatUniform("resolution", width, height)
            native.setFloatUniform("amplitude", amplitude)
            native.setFloatUniform(
                "bendShape",
                if (shape == ArcBendShape.InwardOutward) 1f else 0f,
            )
            native.setFloatUniform(
                "bendAxis",
                if (axis == ArcBendAxis.Vertical) 1f else 0f,
            )
        }

        override fun renderEffect(): RenderEffect? = runCatching {
            // 输入图像由系统绑到名为 "content" 的 child shader，不用自己录制位图。
            AndroidRenderEffect
                .createRuntimeShaderEffect(native, "content")
                .asComposeRenderEffect()
        }.getOrElse {
            Log.w("ArcBend", "RenderEffect 创建失败：本帧不做弯曲", it)
            null
        }
    }
}
