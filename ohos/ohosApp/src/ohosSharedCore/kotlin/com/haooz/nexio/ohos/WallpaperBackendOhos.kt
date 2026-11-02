package com.haooz.nexio.ohos

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.haooz.chedule.data.WallpaperBackend
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.platform.AppFile
import com.haooz.chedule.data.platform.AppFiles
import kotlin.concurrent.AtomicReference
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

/**
 * 壁纸后端（鸿蒙实现）。与 `WallpaperBackendIos` 同一套 skia 编解码，只有屏幕尺寸来源不同
 * （这里读 [OhosEnv]）。
 *
 * 落盘格式 PNG，文件名仍是 `appearance_wallpaper.webp`（格式按内容识别，不看扩展名）。
 *
 * 编解码**不切线程**：鸿蒙的 `Dispatchers.Main` 没初始化，`withContext` 会内联在调用线程上，
 * 切了反而把 Compose 续体从 worker 线程上恢复 —— 那是主线程卡死的原因之一。
 */
internal class OhosWallpaperBackend : WallpaperBackend {

    /** 同步取壁纸，供首帧使用（注入到 `WallpaperStore.synchronousLoader`）。 */
    fun loadNow(): ImageBitmap? {
        if (!AppFiles.isReady) return null
        return runCatching {
            val source = AppFiles.root.resolve(WallpaperStore.FILE_NAME)
            val cache = AppFiles.root.resolve(RAW_CACHE)
            // 壁纸已删但像素缓存还在：作废，否则会读回一张已删的壁纸
            if (cache.exists() && !source.exists()) runCatching { cache.delete() }
            decodeRaw(cache)?.let { return@runCatching it }
            if (!source.exists()) return@runCatching null
            Image.makeFromEncoded(source.readBytes()).toComposeImageBitmap()
                .also { saveRaw(it, cache) }
        }.getOrNull()
    }

    override suspend fun decode(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    }

    override suspend fun encode(image: ImageBitmap, quality: Int): ByteArray? {
        // quality 对 PNG 无意义，保留形参只为对齐接口。
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return null
        return runCatching {
            val src = readToSkiaBitmap(image, w, h) ?: return@runCatching null
            // 压到屏幕尺寸再编码：落盘只服务显示，1× 屏足够，也免得 PNG 体积失控。
            val (bw, bh) = screenBoundsPx()
            val scaled = if (src.width > bw || src.height > bh) {
                val ratio = minOf(bw.toFloat() / src.width, bh.toFloat() / src.height)
                val dw = (src.width * ratio).toInt().coerceAtLeast(1)
                val dh = (src.height * ratio).toInt().coerceAtLeast(1)
                val dst = Bitmap().apply { allocN32Pixels(dw, dh) }
                Canvas(dst).drawImageRect(
                    image = Image.makeFromBitmap(src),
                    src = Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
                    dst = Rect.makeWH(dw.toFloat(), dh.toFloat()),
                )
                dst
            } else {
                src
            }
            Image.makeFromBitmap(scaled)
                // 只能 PNG：鸿蒙这套 skia 没编 JPEG 编码器（`SkJpegEncoder::Encode` 是未定义
                // 符号，调用即跳飞），WEBP 是空实现返回 null。
                .encodeToData(EncodedImageFormat.PNG)
                ?.bytes
        }.getOrNull()
    }

    override suspend fun scale(image: ImageBitmap, width: Int, height: Int): ImageBitmap {
        if (width <= 0 || height <= 0) return image
        if (image.width == width && image.height == height) return image
        return runCatching {
            val src = readToSkiaBitmap(image, image.width, image.height) ?: return@runCatching image
            val dst = Bitmap().apply { allocN32Pixels(width, height) }
            Canvas(dst).drawImageRect(
                image = Image.makeFromBitmap(src),
                src = Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                dst = Rect.makeWH(width.toFloat(), height.toFloat()),
            )
            Image.makeFromBitmap(dst).toComposeImageBitmap()
        }.getOrDefault(image)
    }

    override fun screenBoundsPx(): Pair<Int, Int> {
        val w = OhosEnv.screenWidthPx
        val h = OhosEnv.screenHeightPx
        // 环境还没注入时给一个保守默认值（不影响功能，只影响壁纸烘焙尺寸）
        return if (w > 0 && h > 0) w to h else 1080 to 2400
    }

    // ── 原始像素缓存（纯派生数据，删了只是多解一次）────────────────────────

    private fun saveRaw(image: ImageBitmap, file: AppFile) {
        runCatching {
            val w = image.width
            val h = image.height
            if (w <= 0 || h <= 0) return
            val pixels = IntArray(w * h)
            image.readPixels(
                buffer = pixels,
                startX = 0,
                startY = 0,
                width = w,
                height = h,
                bufferOffset = 0,
                stride = w,
            )
            val bytes = ByteArray(8 + w * h * 4)
            bytes[0] = (w shr 24).toByte()
            bytes[1] = (w shr 16).toByte()
            bytes[2] = (w shr 8).toByte()
            bytes[3] = w.toByte()
            bytes[4] = (h shr 24).toByte()
            bytes[5] = (h shr 16).toByte()
            bytes[6] = (h shr 8).toByte()
            bytes[7] = h.toByte()
            for (i in pixels.indices) {
                val p = pixels[i]
                val o = 8 + i * 4
                bytes[o] = (p and 0xFF).toByte()
                bytes[o + 1] = ((p shr 8) and 0xFF).toByte()
                bytes[o + 2] = ((p shr 16) and 0xFF).toByte()
                bytes[o + 3] = ((p shr 24) and 0xFF).toByte()
            }
            file.writeBytes(bytes)
        }
    }

    private fun decodeRaw(file: AppFile): ImageBitmap? = runCatching {
        if (!file.exists()) return@runCatching null
        val bytes = file.readBytes()
        if (bytes.size < 8) return@runCatching null
        val w = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val h = ((bytes[4].toInt() and 0xFF) shl 24) or ((bytes[5].toInt() and 0xFF) shl 16) or
            ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        if (w <= 0 || h <= 0 || bytes.size != 8 + w * h * 4) return@runCatching null
        val bitmap = Bitmap().apply {
            allocN32Pixels(w, h)
            installPixels(bytes.copyOfRange(8, bytes.size))
        }
        Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }.getOrNull()

    /**
     * CMP 取像素只有 `readPixels`，skia 编码/缩放入口只认 [Bitmap]。
     * `stride` 单位是像素；Int 小端展开成 ByteArray 与 skia N32 布局一致。
     */
    private fun readToSkiaBitmap(image: ImageBitmap, w: Int, h: Int): Bitmap? {
        val pixels = IntArray(w * h)
        image.readPixels(
            buffer = pixels,
            startX = 0,
            startY = 0,
            width = w,
            height = h,
            bufferOffset = 0,
            stride = w,
        )
        val bytes = ByteArray(w * h * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            bytes[i * 4] = (p and 0xFF).toByte()
            bytes[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((p shr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((p shr 24) and 0xFF).toByte()
        }
        return Bitmap().apply {
            allocN32Pixels(w, h)
            installPixels(bytes)
        }
    }

    companion object {
        private const val RAW_CACHE = "appearance_wallpaper.pixels"
    }
}
