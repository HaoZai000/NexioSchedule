package com.haooz.chedule.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.NexioLog
import kotlinx.coroutines.CancellationException

/**
 * 壁纸后端能力 —— 平台实现需提供四项能力。
 *
 * 用接口 + 启动注入而非 expect/actual：`:androidApp` 的 `ScheduleAppearance`
 * 无法被 `:ui-shared` 反向依赖；Android 由 `NexioApplication.onCreate()` 注入
 * `AndroidWallpaperBackend`，iOS 默认 [IosWallpaperBackend]。
 */
interface WallpaperBackend {
    /** 解码壁纸字节为 [ImageBitmap]；失败返回 null（不抛）。 */
    suspend fun decode(bytes: ByteArray): ImageBitmap?

    /** 编码 [ImageBitmap] 为字节（quality 0~100）；失败返回 null。落盘由调用方负责。 */
    suspend fun encode(image: ImageBitmap, quality: Int): ByteArray?

    /**
     * 解码**给定字节**（纯解码，不碰磁盘）
     * 默认实现只为少写平台代码；语义正确的平台应覆盖。
     */
    suspend fun decodeBytes(bytes: ByteArray): ImageBitmap? = decode(bytes)

    /**
     * 只编码、不落盘。
     * 用它存小部件壁纸会把用户的课表壁纸覆盖掉，外观页「跟随壁纸」的深浅色也跟着一起错。
     */
    suspend fun encodeBytes(image: ImageBitmap, quality: Int): ByteArray? = encode(image, quality)

    /** 缩放到指定像素尺寸（宽高比由调用方算好后传入）。 */
    suspend fun scale(image: ImageBitmap, width: Int, height: Int): ImageBitmap

    /** 壁纸的目标存储/解码分辨率，返回 `(宽, 高)` 单位 px，**当前屏幕方向**。 */
    fun screenBoundsPx(): Pair<Int, Int>

    /**
     * 保存尺寸策略：cover 到当前屏幕，只缩不扩，长边封顶 2×屏幕长边；
     * 返回应保存的 (宽, 高)，与原图相同即原样保存。幂等，多层链路重复应用安全。
     *
     * ⚠ 曾用「屏幕长短边」做目标，横图被按短边对齐屏宽砍高度
     * （4000×3000 → 1674×1256），竖屏 cover 再放大 2.2 倍，即「选完壁纸很糊」根因。
     * 短边不足屏幕的小图无解，原样保存、显示侧放大是唯一选择。
     */
    fun saveTargetSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val (targetW, targetH) = screenBoundsPx()
        // cover：缩到刚好覆盖屏幕所需的倍率；>1 说明源图比屏幕小，不缩
        val cover = maxOf(
            targetW / width.toFloat(),
            targetH / height.toFloat()
        )
        if (cover > 1f) return width to height
        // 短边方向已覆盖屏幕；限制长边防极端大图（100MP 级）把显存撑爆
        val maxLong = 2 * maxOf(targetW, targetH)
        val longSide = maxOf(width, height)
        if (longSide <= maxLong) return width to height
        val s = maxLong / longSide.toFloat()
        return (width * s).toInt().coerceAtLeast(1) to (height * s).toInt().coerceAtLeast(1)
    }
}

/**
 * 壁纸存储。
 *
 * ⚠ 存储路径是数据兼容契约：文件名与落盘根目录（[AppFiles.root]）逐字勿改，
 * 否则存量用户壁纸静默丢失。后缀 `.webp` 仅为名字，解码按内容识别格式而非扩展名。
 */
object WallpaperStore {
    private const val TAG = "WallpaperStore"

    /** 壁纸文件名；数据兼容契约，勿改。 */
    const val FILE_NAME = "appearance_wallpaper.webp"

    /** 测光网格边长（16×16）。 */
    private const val GRID = 16

    /** 平台后端。未注入时（桌面/测试）退化成「读不到盘」，表现为没有壁纸。 */
    var backend: WallpaperBackend? = null

    /**
     * 同步取壁纸（读盘 + 解码，不走协程）。首帧前就要拿到，否则首帧只有底色、
     * 等异步加载完再跳成用户设定。
     *
     * 宿主注入：Android = `ScheduleAppearance.loadWallpaper()`（自带 LruCache 与降采样），
     * iOS = skia 同步解码；桌面不注入（无壁纸）。未注入时首帧加载交给异步兜底。
     */
    var synchronousLoader: (() -> ImageBitmap?)? = null

    /**
     * 内存缓存；Android 主缓存在 `ScheduleAppearance.LruCache`，此处仅兜底避免
     * 同会话重复读盘，故改壁纸须两侧都清。
     */
    private var cached: ImageBitmap? = null

    // 变换存于此而非 Activity companion：横竖屏切换 / 进程重建后需靠它把壁纸
    // 摆回用户上次位置，且其为纯数据、无平台成分。

    /** 重映射到当前屏幕后的壁纸 offset（px）。与 [transformScale] 成对。 */
    var wallpaperOffset: Offset = Offset.Zero
        private set

    /** 重映射到当前屏幕后的壁纸 scale（相对 ContentScale.Fit）。 */
    var transformScale: Float = 1f
        private set

    /**
     * 写入当前生效的壁纸变换。
     * ⚠ 须在已 remap 到当前屏幕后调用，否则旋转一圈画面会漂。
     */
    fun saveTransform(offset: Offset, scale: Float) {
        wallpaperOffset = offset
        transformScale = scale
    }

    /**
     * 把壁纸变换从 `fromW×fromH` 屏幕重映射到 `toW×toH`，并**记住结果**。
     *
     * @return 重映射后的 (offset, scale)；没有壁纸或尺寸非法时返回当前值
     */
    fun remapTransform(
        fromW: Float,
        fromH: Float,
        toW: Float,
        toH: Float,
    ): Pair<Offset, Float> {
        val bitmap = cached ?: return wallpaperOffset to transformScale
        val (newOffset, newScale) = WallpaperTransform.remap(
            offset = wallpaperOffset,
            scale = transformScale,
            bitmapW = bitmap.width,
            bitmapH = bitmap.height,
            fromW = fromW,
            fromH = fromH,
            toW = toW,
            toH = toH,
        )
        wallpaperOffset = newOffset
        transformScale = newScale
        return newOffset to newScale
    }

    /** 取当前生效壁纸；没有返回 null。命中缓存则不解码、不读盘。 */
    suspend fun load(): ImageBitmap? {
        cached?.let { return it }
        val be = backend ?: return null
        val file = AppFiles.root.resolve(FILE_NAME)
        if (!file.exists()) return null
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            NexioLog.w(TAG, "Failed to read wallpaper", e)
            return null
        }
        val decoded = be.decode(bytes) ?: return null
        cached = decoded
        return decoded
    }

    /**
     * 保存壁纸（按 [WallpaperBackend.saveTargetSize] 策略缩放到 cover 屏幕后编码落盘），
     * 成功返回 true。
     *
     * @param image 原图；短边方向不足屏幕时原样保存（小图无解，显示侧只能放大）
     */
    suspend fun save(image: ImageBitmap): Boolean {
        val be = backend ?: return false
        return try {
            val (targetW, targetH) = be.saveTargetSize(image.width, image.height)
            val scaled = if (targetW == image.width && targetH == image.height) {
                image
            } else {
                be.scale(image = image, width = targetW, height = targetH)
            }
            val bytes = be.encode(scaled, quality = 80) ?: return false
            AppFiles.root.resolve(FILE_NAME).writeBytes(bytes)
            cached = scaled
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NexioLog.w(TAG, "Failed to save wallpaper", e)
            false
        }
    }

    /** 删磁盘文件并清内存缓存（持久化清除）。 */
    fun clear() {
        cached = null
        try {
            val file = AppFiles.root.resolve(FILE_NAME)
            if (!file.delete() && file.exists()) NexioLog.w(TAG, "Failed to delete wallpaper")
        } catch (e: Exception) {
            NexioLog.w(TAG, "Failed to delete wallpaper", e)
        }
    }

    /**
     * 当前内存里的壁纸实例（不解码、不读盘）。
     * 供宿主层用 `===` 做身份比较以决定是否回收；换实例会误回收正在显示的位图。
     */
    fun currentImageBitmap(): ImageBitmap? = cached

    /** 宿主层直接替换缓存实例（如 Android 选完新壁纸、或外部改过盘上文件后）。 */
    fun setCached(image: ImageBitmap?) {
        cached = image
    }

    /**
     * 16×16 网格**感知加权平均亮度**（0.299R + 0.587G + 0.114B），阈值 128。
     * ⚠ 与 Android 侧一致 —— 阈值改了会让「跟随壁纸」的主题在临界图上翻转。
     *
     * @return null = 无壁纸或尺寸非法（调用方按「未测光」处理）
     */
    fun measureLightness(image: ImageBitmap?): Boolean? {
        if (image == null || image.width <= 0 || image.height <= 0) return null
        return try {
            val pixels = image.toPixelMap()
            var sum = 0.0
            for (x in 0 until GRID) {
                for (y in 0 until GRID) {
                    // 网格中心点采样，等比映射到原图坐标
                    val px = ((x + 0.5f) / GRID * image.width).toInt()
                        .coerceIn(0, image.width - 1)
                    val py = ((y + 0.5f) / GRID * image.height).toInt()
                        .coerceIn(0, image.height - 1)
                    // PixelMap 返回 Color（分量 0f~1f），不是打包的 Int
                    val c = pixels[px, py]
                    val r = c.red * 255f
                    val g = c.green * 255f
                    val b = c.blue * 255f
                    sum += 0.299 * r + 0.587 * g + 0.114 * b
                }
            }
            (sum / (GRID * GRID)) >= 128
        } catch (e: Exception) {
            NexioLog.w(TAG, "Failed to measure wallpaper lightness", e)
            null
        }
    }
}
