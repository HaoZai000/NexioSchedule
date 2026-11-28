/** 桌面小部件壁纸存储 - 每个小部件一种图，变换（偏移/缩放）一并落盘 */
package com.haooz.chedule.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.storage.AppStorage
import kotlinx.coroutines.CancellationException

/**
 * 小部件壁纸。
 *
 * 与 App 壁纸（[WallpaperStore]）分开存：小部件是独立于主界面的另一张画布，
 * 用户给课表换壁纸不该顺手改掉桌面组件的样子。共用同一个 [WallpaperBackend] 做编解码。
 *
 * 文件按「小部件种类」分槽（[key]），不是按页面序号 —— 序号会随页面增减漂移，
 * 种类名是稳定的数据契约。
 */
object WidgetWallpaperStore {
    private const val TAG = "WidgetWallpaperStore"

    /** 壁纸槽位（数据契约）：今日 4×2 / 今明 4×2 / 2×2。 */
    const val KEY_TODAY = "today"
    const val KEY_TODAY_TOMORROW = "today_tomorrow"
    const val KEY_SMALL = "small"

    /** 小部件种类 → 落盘文件名。**逐字勿改**，改了等于用户的组件壁纸凭空消失。 */
    fun fileNameFor(key: String): String = "widget_wallpaper_$key.webp"

    /** 偏好文件名（偏移/缩放）。 */
    private const val PREFS = "widget_wallpaper_prefs"

    /**
     * 落盘长边上限（px）。
     * 小部件最大也就一两千像素，按屏幕分辨率存纯属浪费内存和解码时间。
     */
    private const val MAX_SIDE = 1600

    /**
     * 变更计数。预览页靠它触发重读 —— 位图本身不进全局 state，
     * 否则换一张图会把整棵界面标记成脏的。
     */
    var revision by mutableIntStateOf(0)
        private set

    private val cache = mutableMapOf<String, ImageBitmap?>()

    private fun fileFor(key: String) = AppFiles.root.resolve(fileNameFor(key))

    /** 是否已经有壁纸（只看磁盘，不解码）。 */
    fun exists(key: String): Boolean = try {
        fileFor(key).exists()
    } catch (e: Exception) {
        NexioLog.w(TAG, "Failed to check widget wallpaper: key=$key", e)
        false
    }

    /** 当前内存里的实例（不解码、不读盘）；没加载过返回 null。 */
    fun cached(key: String): ImageBitmap? = cache[key]

    /**
     * 读壁纸；没有或解码失败返回 null。命中缓存则不读盘。
     *
     * @param forceReload 跳过内存缓存强制读盘。刚写完盘要立刻看到新图时用 ——
     *   缓存里那份可能还是旧实例（尤其是「换图但 key 不变」的情况）。
     */
    suspend fun load(key: String, forceReload: Boolean = false): ImageBitmap? {
        if (!forceReload) cache[key]?.let { return it }
        val backend = WallpaperStore.backend ?: return null
        val file = fileFor(key)
        if (!file.exists()) return null
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            NexioLog.w(TAG, "Failed to read widget wallpaper: key=$key", e)
            return null
        }
        val decoded = backend.decodeBytes(bytes) ?: return null
        cache[key] = decoded
        return decoded
    }

    /** 保存壁纸（超长边先缩到 [MAX_SIDE]，再编码落盘）。成功返回 true。 */
    suspend fun save(key: String, image: ImageBitmap): Boolean {
        val backend = WallpaperStore.backend ?: return false
        return try {
            val longSide = maxOf(image.width, image.height)
            val scaled = if (longSide > MAX_SIDE) {
                val s = MAX_SIDE / longSide.toFloat()
                backend.scale(
                    image = image,
                    width = (image.width * s).toInt().coerceAtLeast(1),
                    height = (image.height * s).toInt().coerceAtLeast(1),
                )
            } else {
                image
            }
            // ⚠ 必须用 encodeBytes：encode 在 Android 上会写 App 壁纸文件
            val bytes = backend.encodeBytes(scaled, quality = 85) ?: return false
            fileFor(key).writeBytes(bytes)
            cache[key] = scaled
            revision++
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NexioLog.w(TAG, "Failed to save widget wallpaper: key=$key", e)
            false
        }
    }

    /** 删磁盘文件并清缓存。 */
    fun clear(key: String) {
        cache.remove(key)
        try {
            val file = fileFor(key)
            if (!file.delete() && file.exists()) NexioLog.w(TAG, "Failed to delete widget wallpaper: key=$key")
        } catch (e: Exception) {
            NexioLog.w(TAG, "Failed to delete widget wallpaper: key=$key", e)
        }
        revision++
    }

    /** 读取景（归一化焦点 + 缩放）。 */
    fun loadCrop(key: String): WidgetWallpaperCrop {
        val store = AppStorage.store(PREFS)
        return WidgetWallpaperCrop(
            centerX = store.getFloat("crop_center_x_$key", 0.5f),
            centerY = store.getFloat("crop_center_y_$key", 0.5f),
            scale = store.getFloat("crop_scale_$key", 1f),
        )
    }

    /**
     * 写取景。
     *
     * 存的是归一化焦点 + 缩放倍率（不是像素偏移）—— 这样任何尺寸的容器都能现算裁剪区，
     * 组件高度变了壁纸自动重新取景，不会被拉伸走样。
     */
    fun saveCrop(key: String, crop: WidgetWallpaperCrop) {
        AppStorage.store(PREFS).edit {
            putFloat("crop_center_x_$key", crop.centerX)
            putFloat("crop_center_y_$key", crop.centerY)
            putFloat("crop_scale_$key", crop.scale)
        }
    }

    /** 读组件深浅色档位；默认跟随壁纸。 */
    fun loadThemeMode(key: String): ThemeMode =
        ThemeMode.fromPrefsValue(AppStorage.store(PREFS).getString("theme_mode_$key", ""))

    /** 写组件深浅色档位。 */
    fun saveThemeMode(key: String, mode: ThemeMode) {
        AppStorage.store(PREFS).edit { putString("theme_mode_$key", mode.prefsValue) }
    }

    /** 读外观参数（字体缩放 / 模糊）。 */
    fun loadAppearance(key: String): WidgetAppearance {
        val store = AppStorage.store(PREFS)
        return WidgetAppearance(
            textScale = store.getFloat("text_scale_$key", 1f),
            wallpaperBlur = store.getFloat("wallpaper_blur_$key", WIDGET_WALLPAPER_BLUR_DEFAULT),
        )
    }

    /** 写外观参数。 */
    fun saveAppearance(key: String, appearance: WidgetAppearance) {
        AppStorage.store(PREFS).edit {
            putFloat("text_scale_$key", appearance.textScale)
            putFloat("wallpaper_blur_$key", appearance.wallpaperBlur)
        }
    }
}

/**
 * 小部件外观参数（按种类各存一份，与壁纸同槽位）。
 *
 * @param textScale 字号倍率（1 = 原始）
 * @param wallpaperBlur 壁纸模糊半径（dp）
 *
 * 卡片不做磨砂：有壁纸就是固定半透明底色（[WIDGET_CARD_ALPHA_WITH_WALLPAPER]）压在照片上，
 * 没壁纸就是纯色。真机 RemoteViews 表达不了动态 alpha，所以只能是静态资源 + 常量。
 */
data class WidgetAppearance(
    val textScale: Float = 1f,
    val wallpaperBlur: Float = WIDGET_WALLPAPER_BLUR_DEFAULT,
)

/** 壁纸模糊的出厂值（dp）。浮层滑杆的锚点也取这个数，两处不许各写各的。 */
const val WIDGET_WALLPAPER_BLUR_DEFAULT: Float = 8f

/** 有壁纸时课程卡片底色的不透明度（真机侧靠静态 drawable 落地，所以是常量）。 */
const val WIDGET_CARD_ALPHA_WITH_WALLPAPER: Float = 0.6f

/**
 * 小部件壁纸的取景。
 *
 * @param offset 像素偏移（相对「框中心」）
 * @param scale 相对 ContentScale.Fit 的缩放倍数
 * @param boxWidth / boxHeight 记录这份取景时那块框的像素尺寸；渲染到别的尺寸时用它换算
 */
data class WidgetWallpaperTransform(
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val boxWidth: Float = 0f,
    val boxHeight: Float = 0f,
)
