// squircle 的遮罩位图（鸿蒙端）。
// 官方实现要 `asSkiaBitmap` + `Image.makeShader` + `asComposeShader`，这套 klib 不公开；
// `isRuntimeShaderSupported()` 为 false，遮罩路径走不到，这里只给占位与降级。
package top.yukonga.miuix.kmp.squircle.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode

internal actual fun makeAlphaImageBitmap(size: Int, alphaBytes: ByteArray): ImageBitmap =
    ImageBitmap(size, size)

/** 用 [image] 本身做 `Clamp` 平铺的 ImageShader（默认双线性采样）。 */
internal actual fun makeLinearShader(image: ImageBitmap): Shader =
    ImageShader(
        image = image,
        tileModeX = TileMode.Clamp,
        tileModeY = TileMode.Clamp,
    )
