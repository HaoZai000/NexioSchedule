package com.haooz.chedule.data

import android.graphics.Bitmap
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

/** 课表壁纸由 [ScheduleAppearance] 读写。替换或删除壁纸时，需同时清除其位图缓存和 [WallpaperStore] 缓存。 */
class AndroidWallpaperBackend : WallpaperBackend {

    override suspend fun decode(bytes: ByteArray): ImageBitmap? {
        // 课表壁纸直接从缓存或文件加载；小部件字节由 decodeBytes 解码。
        return ScheduleAppearance.loadWallpaper()?.asImageBitmap()
    }

    /** 首帧同步读取壁纸，避免先显示纯色背景。 */
    fun loadNow(): ImageBitmap? = ScheduleAppearance.loadWallpaper()?.asImageBitmap()

    override suspend fun encode(image: ImageBitmap, quality: Int): ByteArray? {
        // saveWallpaper 写入文件；返回实际落盘的编码字节。
        if (!ScheduleAppearance.saveWallpaper(image.asAndroidBitmap())) return null
        return readWallpaperBytes()
    }

    /**
     * 纯解码给定字节。
     * ⚠ 不能落到 [decode]：那是「读 App 壁纸文件」，会把小部件壁纸读成课表壁纸。
     */
    override suspend fun decodeBytes(bytes: ByteArray): ImageBitmap? = try {
        android.graphics.BitmapFactory
            .decodeByteArray(bytes, 0, bytes.size)
            ?.asImageBitmap()
    } catch (_: Exception) {
        null
    }

    /**
     * 纯编码，不落盘、不碰 LruCache。
     * ⚠ 不能落到 [encode]：那会调 `ScheduleAppearance.saveWallpaper`，把用户的课表壁纸
     * （以及外观页「跟随壁纸」的深浅色）覆盖成小部件壁纸。
     */
    override suspend fun encodeBytes(image: ImageBitmap, quality: Int): ByteArray? = try {
        val out = java.io.ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        val ok = image.asAndroidBitmap().compress(format, quality.coerceIn(1, 100), out)
        if (ok) out.toByteArray() else null
    } catch (_: Exception) {
        null
    }

    override suspend fun scale(
        image: ImageBitmap,
        width: Int,
        height: Int,
    ): ImageBitmap {
        val src = image.asAndroidBitmap()
        val scaled: Bitmap = src.scale(width, height, true)
        // scale 尺寸相同可能返回原图（共享只读实例，可变性未知），拷一份可变的避免被改坏。
        val safe = if (scaled === src) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            scaled
        }
        return safe.asImageBitmap()
    }

    override fun screenBoundsPx(): Pair<Int, Int> {
        val metrics: DisplayMetrics = ScheduleAppearance.displayMetrics()
        // 方向感知：横图在竖屏需按 cover 保留高度，长短边口径会把高度砍到屏宽。
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun readWallpaperBytes(): ByteArray? = try {
        java.io.File(
            ScheduleAppearance.appFilesDir(),
            WallpaperStore.FILE_NAME,
        ).readBytes()
    } catch (_: Exception) {
        null
    }
}
