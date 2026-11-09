/** 把用户设的小部件壁纸铺到真机 RemoteViews 上（含取景换算与圆角） */
package com.haooz.chedule.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asImageBitmap
import com.haooz.chedule.R
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.WidgetWallpaperStore
import com.haooz.chedule.data.appearance.ThemeMode
import com.haooz.chedule.data.calculateWidgetCropRect
import com.haooz.chedule.data.platform.AppFiles
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 小部件壁纸落到 RemoteViews 上。
 *
 * 为什么是「先烘焙成位图」：RemoteViews 没法给 View 设任意 Drawable，
 * 也不支持裁剪到圆角。所以取景（fit + 缩放 + 平移）和圆角都在这里画进一张图，
 * 再让布局里垫底的 ImageView 铺满。
 *
 * ⚠ 位图走 Binder，整包预算 1MB：像素数必须封顶（[MAX_PIXELS]），
 * 超了会抛 TransactionTooLargeException，表现为组件变白/不刷新。
 */
object WidgetWallpaperRender {

    /** 组件渲染尺寸（dp）。 */
    data class WidgetSize(val widthDp: Int, val heightDp: Int)

    /**
     * 组件可能的渲染尺寸。
     *
     * API 31+ 直接用 [AppWidgetManager.OPTION_APPWIDGET_SIZES]（桌面报的候选尺寸），
     * 配合 [responsiveViews] 让桌面按实际尺寸挑一份 —— 这是唯一能拿到「真实尺寸」的办法。
     * 低版本退回 min/max 两种朝向。
     */
    fun renderSizes(
        manager: AppWidgetManager,
        appWidgetId: Int,
        fallback: WidgetSize,
    ): List<WidgetSize> {
        val options = try {
            manager.getAppWidgetOptions(appWidgetId) ?: Bundle.EMPTY
        } catch (_: Exception) {
            Bundle.EMPTY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = androidx.core.os.BundleCompat
                .getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                .orEmpty()
                .map { WidgetSize(it.width.roundToInt(), it.height.roundToInt()) }
                .filter { it.widthDp >= 80 && it.heightDp >= 80 }
                .distinct()
            if (sizes.isNotEmpty()) return sizes
        }
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, fallback.widthDp)
            .takeIf { it > 0 } ?: fallback.widthDp
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallback.heightDp)
            .takeIf { it > 0 } ?: fallback.heightDp
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
            .coerceAtLeast(minWidth)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
            .coerceAtLeast(minHeight)
        return listOf(WidgetSize(minWidth, maxHeight), WidgetSize(maxWidth, minHeight)).distinct()
    }

    /**
     * 尺寸指纹。**必须进内容签名**：拉伸组件后尺寸变了但课程数据没变，
     * 签名不变就会被 shouldSkip 跳过 → 桌面继续挂着按旧尺寸烘的图，卡片就错位。
     */
    fun sizeSignature(
        manager: AppWidgetManager,
        appWidgetId: Int,
        fallback: WidgetSize,
    ): String = renderSizes(manager, appWidgetId, fallback)
        .joinToString(",") { "${it.widthDp}x${it.heightDp}" }

    /** 按尺寸分包：API 31+ 交给桌面按实际尺寸挑；低版本退回「横/竖」两份。 */
    fun responsiveViews(views: List<Pair<WidgetSize, RemoteViews>>): RemoteViews = when {
        views.size == 1 -> views.first().second
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> RemoteViews(
            views.associate { (size, remote) ->
                SizeF(size.widthDp.toFloat(), size.heightDp.toFloat()) to remote
            }
        )
        else -> RemoteViews(views[1].second, views[0].second)
    }

    /**
     * 单张壁纸位图的像素上限。
     * 位图用 RGB_565（圆角已交给宿主裁，不需要 alpha）→ 2 字节/像素，
     * 34 万像素 ≈ 680KB，在 Binder 1MB 预算内给文案/色条位图留了余量。
     * 之前是 ARGB + 16 万像素（640KB），只有 600px 宽，铺到真机上明显发虚。
     */
    private const val MAX_PIXELS = 340_000

    /**
     * 开了模糊时的像素上限（位图改用 ARGB_8888）。
     * RGB_565 每通道只有 5~6 位，模糊出来大片渐变必然出现色阶断层；
     * 但 8888 是 4 字节/像素，只好把像素数压到 20 万（≈800KB）——
     * 反正已经糊了，分辨率低一点看不出来。
     */
    private const val BLUR_MAX_PIXELS = 200_000

    private val bitmapCache = mutableMapOf<String, Bitmap?>()
    private var cacheRevision = -1

    /** 烘焙结果缓存：桌面会报多个候选尺寸，每个尺寸都要一份，不能每次重糊 */
    private val renderedCache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > 8
    }

    /** 该组件有没有设壁纸。 */
    fun hasWallpaper(key: String): Boolean = WidgetWallpaperStore.exists(key)

    /**
     * 「正在上课」卡片的背景资源。
     *
     * 无壁纸时用原来的半透明蓝（叠在组件底色上）；有壁纸时容器底色是透明的，
     * 半透明蓝会直接漏出照片、字压在图上读不出来 —— 换等价的不透明色。
     */
    fun activeCardBackgroundRes(key: String, dark: Boolean, jinming: Boolean = false): Int {
        if (!hasWallpaper(key)) {
            return if (jinming) R.drawable.widget_card_active_background_jinming
            else R.drawable.widget_card_active_background
        }
        return when {
            jinming && dark -> R.drawable.widget_card_active_half_jinming_dark
            jinming -> R.drawable.widget_card_active_half_jinming_light
            dark -> R.drawable.widget_card_active_half_dark
            else -> R.drawable.widget_card_active_half_light
        }
    }

    /**
     * 普通课程卡片的背景资源。
     *
     * 无壁纸时沿用 `widget_card_background(_jinming)`（靠 values-night 限定符跟系统）；
     * 有壁纸时必须换成写死深浅的两套 —— 资源 id 是宿主进程按宿主配置解析的，
     * 不写死的话「按照片锁定的深浅」在真机上根本不生效。
     */
    fun cardBackgroundRes(key: String, dark: Boolean, jinming: Boolean = false): Int {
        if (!hasWallpaper(key)) {
            return if (jinming) R.drawable.widget_card_background_jinming
            else R.drawable.widget_card_background
        }
        return when {
            jinming && dark -> R.drawable.widget_card_half_jinming_dark
            jinming -> R.drawable.widget_card_half_jinming_light
            dark -> R.drawable.widget_card_half_dark
            else -> R.drawable.widget_card_half_light
        }
    }

    /** 卡片先给圆角 shape，再按莫奈色染一层 tint。 */
    fun applyCardBackground(
        views: RemoteViews,
        cardId: Int,
        key: String,
        dark: Boolean,
        active: Boolean,
        monet: WidgetMonetColors.Palette? = null,
        jinming: Boolean = false,
    ) {
        views.setInt(
            cardId,
            "setBackgroundResource",
            if (active) activeCardBackgroundRes(key, dark, jinming)
            else cardBackgroundRes(key, dark, jinming),
        )
        monet?.let {
            WidgetMonetColors.tintBackground(
                views,
                cardId,
                WidgetMonetColors.cardColor(it, active, hasWallpaper(key)),
            )
        }
    }

    /**
     * 组件模式带来的上下留白（dp）。
     * 与各 provider 的 `applyWidgetMode` 同一张表：根容器被这段 padding 内缩，
     * 垫底的 ImageView 也跟着内缩，壁纸按「内缩后」的高度烘焙才不会上下压扁。
     */
    fun verticalPaddingDp(mode: Int): Pair<Float, Float> = when (mode) {
        1 -> 12f to 14f
        2 -> 8f to 10f
        3 -> 21f to 5f
        4 -> 17f to 2f
        else -> 0f to 0f
    }

    /**
     * 壁纸相关的内容指纹。
     * 换图、挪取景、改深浅档位都必须让组件重绘 —— 这些都不在课程数据里，
     * 漏进签名就会出现「应用了但桌面没变」。
     */
    fun signaturePart(key: String): String {
        if (!WidgetWallpaperStore.exists(key)) return "wp:0"
        val crop = WidgetWallpaperStore.loadCrop(key)
        val mode = WidgetWallpaperStore.loadThemeMode(key)
        val appearance = WidgetWallpaperStore.loadAppearance(key)
        return buildString {
            append("wp:1")
            // 换图但取景恰好一样时，只有 revision 能区分出「图变了」
            append(':').append(WidgetWallpaperStore.revision)
            append(':').append(crop.centerX).append(':').append(crop.centerY)
            append(':').append(crop.scale)
            append(':').append(mode.prefsValue)
            append(':').append(appearance.wallpaperBlur)
            append(':').append(appearance.textScale)
        }
    }

    /**
     * 组件该用深色还是浅色。
     *
     * 有壁纸且档位是「跟随壁纸」时按照片明暗锁定 —— 与 App 内预览同一套判定
     * （直接复用 [WallpaperStore.measureLightness]，两处口径不会漂）。
     */
    fun resolveDark(context: Context, key: String): Boolean {
        val systemDark = WidgetTextSizes.isDark(context)
        if (!WidgetWallpaperStore.exists(key)) return systemDark
        return when (WidgetWallpaperStore.loadThemeMode(key)) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.FOLLOW_APP -> systemDark
            ThemeMode.FOLLOW_WALLPAPER -> {
                val bitmap = bitmapFor(key) ?: return systemDark
                WallpaperStore.measureLightness(bitmap.asImageBitmap())?.let { !it } ?: systemDark
            }
        }
    }

    /**
     * 铺壁纸；没有壁纸则隐藏垫底 ImageView 并恢复原来的圆角底色。
     *
     * 卡片不做任何磨砂烘焙：卡片底色是静态半透明 drawable（见 [cardBackgroundRes]），
     * 位置与尺寸全交给布局。少一层「按几何裁图」，就没有位图和视图各缩放一次的机会。
     *
     * @param imageId 布局里垫底的 ImageView（必须在容器之前、且铺满整块）
     * @param containerId 承载内容的容器，有壁纸时它的底色要让开
     * @param normalBackgroundRes 无壁纸时容器用的圆角底色（各组件自己的）
     */
    fun apply(
        views: RemoteViews,
        context: Context,
        key: String,
        appWidgetId: Int,
        imageId: Int,
        containerId: Int,
        normalBackgroundRes: Int,
        /** 根容器上下留白（见 [verticalPaddingDp]），壁纸按内缩后的高度烘焙 */
        insetTopDp: Float = 0f,
        insetBottomDp: Float = 0f,
        /** 该份 RemoteViews 对应的渲染尺寸（dp）；给了就按它烘焙，不再读 options */
        size: WidgetSize? = null,
        /** 莫奈取色；为空时保持原配色 */
        monet: WidgetMonetColors.Palette? = null,
    ) {
        val appearance = WidgetWallpaperStore.loadAppearance(key)
        val bitmap = if (hasWallpaper(key)) {
            val density = context.resources.displayMetrics.density
            val blurDp = appearance.wallpaperBlur
            val maxPixels = if (blurDp > 0.5f) BLUR_MAX_PIXELS else MAX_PIXELS
            val (width, height, pixelScale) = if (size != null) {
                widgetPixelSize(density, size.widthDp, size.heightDp, maxPixels)
            } else {
                widgetPixelSize(context, appWidgetId, density, maxPixels)
            }
            // 留白是作用在视图上的真实像素，换算到位图坐标要乘 pixelScale
            val contentHeight = (height - (insetTopDp + insetBottomDp) * density * pixelScale)
                .roundToInt()
                .coerceAtLeast(1)
            renderBitmap(
                key = key,
                width = width,
                height = contentHeight,
                density = density,
                blurDp = blurDp,
                pixelScale = pixelScale,
            )
        } else {
            null
        }
        if (bitmap == null) {
            views.setViewVisibility(imageId, View.GONE)
            views.setInt(containerId, "setBackgroundResource", normalBackgroundRes)
            monet?.let { WidgetMonetColors.tintBackground(views, containerId, it.surface) }
            return
        }
        views.setViewVisibility(imageId, View.VISIBLE)
        views.setImageViewBitmap(imageId, bitmap)
        // 圆角交给宿主按真实尺寸裁（背景只是给 outline 用，本身全透明）
        views.setInt(
            imageId,
            "setBackgroundResource",
            if (WidgetTextSizes.isXiaomi) R.drawable.widget_wallpaper_clip_xiaomi
            else R.drawable.widget_wallpaper_clip
        )
        views.setBoolean(imageId, "setClipToOutline", true)
        // 有壁纸时容器底色必须透明，否则整张照片被原来的圆角底色盖住
        views.setInt(containerId, "setBackgroundColor", Color.TRANSPARENT)
    }

    /** 组件当前像素尺寸（按桌面给的选项），并按像素上限封顶。 */
    private fun widgetPixelSize(
        context: Context,
        appWidgetId: Int,
        density: Float,
        maxPixels: Int,
    ): Triple<Int, Int, Float> {
        val options: Bundle? = try {
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        } catch (_: Exception) {
            null
        }
        val widthDp = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0)
            .takeIf { it > 0 } ?: 250
        val heightDp = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0)
            .takeIf { it > 0 } ?: 110
        return widgetPixelSize(density, widthDp, heightDp, maxPixels)
    }

    private fun widgetPixelSize(
        density: Float,
        widthDp: Int,
        heightDp: Int,
        maxPixels: Int,
    ): Triple<Int, Int, Float> {
        var width = (widthDp * density).roundToInt().coerceAtLeast(1)
        var height = (heightDp * density).roundToInt().coerceAtLeast(1)
        val pixels = width.toFloat() * height
        var factor = 1f
        if (pixels > maxPixels) {
            factor = sqrt(maxPixels / pixels)
            width = (width * factor).roundToInt().coerceAtLeast(1)
            height = (height * factor).roundToInt().coerceAtLeast(1)
        }
        return Triple(width, height, factor)
    }

    /** 取景（归一化焦点 + 缩放）画进一张位图；圆角由宿主侧 clipToOutline 负责。 */
    private fun renderBitmap(
        key: String,
        width: Int,
        height: Int,
        density: Float,
        blurDp: Float,
        pixelScale: Float,
    ): Bitmap? {
        val source = bitmapFor(key) ?: return null
        if (width <= 0 || height <= 0) return null
        val cacheKey = buildString {
            append(key).append('|').append(width).append('x').append(height)
            append('|').append(blurDp).append('|').append(pixelScale)
            append('|').append(WidgetWallpaperStore.revision)
        }
        synchronized(renderedCache) { renderedCache[cacheKey] }?.let { return it }
        // 与 App 内/参考实现同一套：归一化焦点 + 缩放，按当前容器尺寸现算裁剪区
        val crop = WidgetWallpaperStore.loadCrop(key)
        val framing = calculateWidgetCropRect(
            bitmapW = source.width,
            bitmapH = source.height,
            containerW = width.toFloat(),
            containerH = height.toFloat(),
            crop = crop,
        )
        val dest = RectF(framing.left, framing.top, framing.right, framing.bottom)

        val output = Bitmap.createBitmap(
            width,
            height,
            if (blurDp > 0.5f) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, dest, paint)
        // 「壁纸模糊」：画完在成品上做真模糊（两遍盒式 ≈ 高斯）。
        // 半径先按「位图被压缩的比例」折算，再留 0.7 的余量 —— 组件实际尺寸通常比
        // OPTION_APPWIDGET_MIN_* 大，位图会被拉伸，模糊半径跟着一起放大。
        if (blurDp > 0.5f) {
            blurInPlace(output, (blurDp * density * pixelScale * 0.7f).roundToInt())
        }

        synchronized(renderedCache) { renderedCache[cacheKey] = output }
        return output
    }

    /** 就地做两遍可分离盒式模糊（近似高斯），避免马赛克感。 */
    private fun blurInPlace(bitmap: Bitmap, radius: Int) {
        if (radius < 2 || bitmap.width <= 0 || bitmap.height <= 0) return
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(2) {
            boxBlurPass(pixels, w, h, radius, horizontal = true)
            boxBlurPass(pixels, w, h, radius, horizontal = false)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** 一维滑动窗口盒式模糊；alpha 统一补 255（壁纸本身不透明）。 */
    private fun boxBlurPass(pixels: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val length = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = radius * 2 + 1
        val line = IntArray(length)
        for (i in 0 until lines) {
            for (j in 0 until length) {
                line[j] = if (horizontal) pixels[i * w + j] else pixels[j * w + i]
            }
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val c = line[k.coerceIn(0, length - 1)]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (j in 0 until length) {
                val out = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                if (horizontal) pixels[i * w + j] = out else pixels[j * w + i] = out
                val remove = line[(j - radius).coerceIn(0, length - 1)]
                val add = line[(j + radius + 1).coerceIn(0, length - 1)]
                r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    /** 解码并缓存壁纸位图；壁纸换过一次（revision 变）就整体作废。 */
    private fun bitmapFor(key: String): Bitmap? {
        if (cacheRevision != WidgetWallpaperStore.revision) {
            bitmapCache.clear()
            cacheRevision = WidgetWallpaperStore.revision
        }
        if (bitmapCache.containsKey(key)) return bitmapCache[key]
        val decoded = try {
            val file = AppFiles.root.resolve(WidgetWallpaperStore.fileNameFor(key))
            if (!file.exists()) {
                null
            } else {
                val bytes = file.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
        bitmapCache[key] = decoded
        return decoded
    }
}
