package com.haooz.chedule.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.scale
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.haooz.chedule.data.appearance.AppearancePrefs
import com.haooz.chedule.data.appearance.CardContentAlignment
import com.haooz.chedule.data.appearance.CardRefractionLevel
import com.haooz.chedule.data.appearance.CardTextColor
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KeyValueStore
import com.haooz.chedule.data.storage.getStringOrNull
import java.io.File
import java.io.FileOutputStream

// ════════════════════════════════════════════════════════════════════════
//  课表外观（Android 宿主侧）：启动初始化 + 老数据迁移 + 壁纸文件 I/O。
//  参数读写已下沉 :core 的 AppearanceStore；独立 `appearance_settings` 文件存储，
//  不与课程数据混存，也不进全量备份。
// ════════════════════════════════════════════════════════════════════════

/** 卡片内容对齐方式 / 文字配色 / 透镜折射强度：已下沉 :core（同包名 CardTextColor.kt） */

/**
 * 1.5.2 老外观快照的解析结构（**仅迁移用**）。
 *
 * 字段名 = 旧 JSON 键名，逐字不能改；解析出来只做一件事：用 Gson 重新序列化进新文件
 * （[ScheduleAppearance.migrateLegacyAppearanceIfNeeded]）。参数语义与默认值都在共享层
 * `AppearanceStyle` / `AppearanceConfig`，这里不参与取值，所以不提供 safe getter。
 *
 * 枚举字段声明为可空：Gson UnsafeAllocator 使 Kotlin 默认值不生效。
 */
data class CombinationStyle(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    /** offset/scale 保存时的屏幕宽高（px）；0=未记录，加载时不重映射 */
    val offsetRefW: Float = 0f,
    val offsetRefH: Float = 0f,
    /** 课程卡片模糊半径（dp） */
    val cardBlur: Float = 4f,
    val cardAlpha: Float = 0.12f,
    /** 有壁纸时白/黑底不透明度；null=未设置，用默认 */
    val cardSurfaceAlpha: Float? = null,
    val cardHeight: Float = 54f,
    val cardCornerRadius: Float = 10f,
    val wallpaperBrightness: Float = 0f,
    /** null=无壁纸/未测光 */
    val wallpaperIsLight: Boolean? = null,
    val showBreakDividers: Boolean = true,
    val cardContentAlignment: CardContentAlignment? = null,
    val cardTextColor: CardTextColor? = null,
    val cardTextScale: Float = 1f,
    val showClassroom: Boolean = true,
    val showTeacher: Boolean = true,
    val cardRefraction: CardRefractionLevel? = null,
    val wallpaperBlur: Boolean = false
) {
    companion object {
        /** 仅用于校验快照键名，不参与取值 */
        private val FIELD_NAMES = setOf(
            "offsetX", "offsetY", "scale", "offsetRefW", "offsetRefH",
            "cardBlur", "cardAlpha", "cardSurfaceAlpha",
            "cardHeight",
            "cardCornerRadius", "wallpaperBrightness", "wallpaperIsLight",
            "showBreakDividers", "cardContentAlignment", "cardTextColor",
            "cardTextScale", "showClassroom", "showTeacher", "cardRefraction",
            "wallpaperBlur"
        )

        /** 至少命中一个已知字段名才采信；键名对不上 Gson 会停在 Java 默认值（如 cardHeight=0 → 课表空白），判为不可用由调用方恢复默认。 */
        fun parseSnapshotOrNull(gson: Gson, json: String): CombinationStyle? {
            val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return null
            if (obj.keySet().none { it in FIELD_NAMES }) return null
            return runCatching { gson.fromJson(json, CombinationStyle::class.java) }.getOrNull()
        }
    }
}

/**
 * 外观存储（Android 宿主侧）。
 *
 * 参数读写已全部下沉到共享层 `AppearanceStore`（`:core`），页面直接调它 —— 这里只剩
 * Android 独有的三件事：**启动初始化 + 1.5.2 老数据迁移**、**壁纸文件 I/O（降采样/LruCache/像素缓存）**、
 * 给 `WallpaperStore` 用的路径与屏幕指标。
 * 迁移用 [CombinationStyle] 解析旧 JSON，所以那个类留着；别再加转发 getter/setter，加了也没人调。
 *
 * 只有一个键：[FILE_STYLE_KEY]（参数 JSON 快照）+ 一个壁纸文件。没有 id 维度。
 */
object ScheduleAppearance {

    // 这两个常量已下沉 :core（CourseRepository 判外观键要用，:core 不能反向依赖 :app）；此处 const 转发取值同源不抄错。
    /** 独立 prefs 文件名 */
    const val FILE = AppearancePrefs.FILE

    /** 唯一的数据键：参数JSON 快照 */
    const val FILE_STYLE_KEY = AppearancePrefs.FILE_STYLE_KEY

    /** 壁纸文件名（存 filesDir） */
    private const val WALLPAPER_FILE = "appearance_wallpaper.webp"

    /**
     * 壁纸解码结果的原始像素缓存（宽、高、ARGB_8888 平铺）。
     * 纯派生数据：删了只会让下次启动多解一次 webp，不影响任何用户设置。
     */
    private const val WALLPAPER_RAW_CACHE = "appearance_wallpaper.pixels"

    /**
     * 供 [WallpaperStore] 复用同一落盘目录。
     * ⚠ 必须与此处 loadWallpaper/saveWallpaper 内部 `File(appContext.filesDir, …)` 逐字一致，否则存量壁纸静默丢失。
     */
    fun appFilesDir(): java.io.File = appContext.filesDir

    /** 屏幕显示指标 —— 供 [WallpaperStore] 计算壁纸目标分辨率（长边/短边）。 */
    fun displayMetrics(): android.util.DisplayMetrics = appContext.resources.displayMetrics

    private lateinit var appContext: Context
    private val gson = Gson()
    private lateinit var prefs: KeyValueStore

    /** 壁纸解码是主要瓶颈，按可用内存做 Lru 缓存。上限须装得下一张 cover 壁纸（3680×2760≈40.5MB，长边封顶 5520×2760≈61MB）；
     * 旧上限 32MB 装不下会被逐出，每次冷启动全量重解（~100ms+40MB 分配）。48MB 下限不可省：小内存机型 maxMemory/4 可能仅几 MB。 */
    private val wallpaperCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        minOf(Runtime.getRuntime().maxMemory() / 4, 96L * 1024 * 1024)
            .coerceAtLeast(48L * 1024 * 1024)
            .toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }


    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        // 走 AppStorage 按名取（Android 侧仍是同一 appearance_settings 文件）
        prefs = AppStorage.store(FILE)
        migrateLegacyAppearanceIfNeeded()
    }

    /**
     * 从旧版主 prefs（course_schedule_prefs）迁移外观数据。
     * 外观曾寄存在主 prefs，键为 combination_style_{id}（自 1.5.2 起此格式），壁纸为 combination_wallpaper_{id}.webp。
     * 不迁移老用户升级会静默丢失全部外观且不报错。兼容基线 1.5.2；更早 comb_xxx 格式不再处理。
     * 幂等：仅在新文件完全为空时执行一次，已有值一律跳过绝不覆盖。
     */
    private fun migrateLegacyAppearanceIfNeeded() {
        if (prefs.all().isNotEmpty()) return
        val legacy = runCatching {
            AppStorage.store(LEGACY_PREFS)
        }.getOrNull() ?: return

        // 迁「当前正在用的那套」：1.5.6 及更早有 current_combination_id 指针，只取首个会在多搭配数据上迁错。
        val legacyIds = legacy.getStringOrNull(LEGACY_KEY_COMBINATION_IDS)
            ?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
            ?.takeIf { it.isNotEmpty() }
        val currentId = runCatching {
            legacy.getLong(LEGACY_KEY_CURRENT_COMBINATION_ID, Long.MIN_VALUE)
        }.getOrDefault(Long.MIN_VALUE)
        val legacyId = when {
            legacyIds == null -> 0L
            currentId in legacyIds -> currentId
            else -> legacyIds.first()
        }

        // 快照坏掉（键名对不上/JSON 损坏）时不迁，让共享层按壁纸重测光，避免把默认值当用户设置落盘。
        val style = legacy.getStringOrNull("$LEGACY_STYLE_PREFIX$legacyId")
            ?.let { CombinationStyle.parseSnapshotOrNull(gson, it) }
        if (style != null) {
            prefs.edit { putString(FILE_STYLE_KEY, gson.toJson(style)) }
        }
        migrateLegacyWallpaperFile(legacyId)
    }

    /** 壁纸文件改名迁移：webp 为主，旧 png 兜底（1.5.2 前后都写过 png） */
    private fun migrateLegacyWallpaperFile(legacyId: Long) {
        val target = File(appContext.filesDir, WALLPAPER_FILE)
        if (target.exists()) return
        val candidates = listOf(
            File(appContext.filesDir, WALLPAPER_FILE),
            File(appContext.filesDir, "$LEGACY_WALLPAPER_PREFIX$legacyId.webp"),
            File(appContext.filesDir, "$LEGACY_WALLPAPER_PREFIX$legacyId.png")
        )
        candidates.firstOrNull { it.exists() }?.let { src ->
            runCatching { src.copyTo(target, overwrite = true) }
        }
    }

    // ── 旧版存储位置（仅迁移期使用）──────────────────────────
    private const val LEGACY_PREFS = "course_schedule_prefs"
    private const val LEGACY_STYLE_PREFIX = "combination_style_"
    private const val LEGACY_WALLPAPER_PREFIX = "combination_wallpaper_"
    private const val LEGACY_KEY_COMBINATION_IDS = "combination_ids"
    private const val LEGACY_KEY_CURRENT_COMBINATION_ID = "current_combination_id"

    // ── 壁纸位图 ────────────────────────────────────────────

    /** 壁纸目标存储/解码分辨率：当前屏幕 (宽, 高)，方向感知。
     * ⚠ 曾用长短边规避横竖屏漂移，代价是横图在竖屏被按短边砍高（4000×3000→1674×1256）cover 放大 2.2 倍 = 「选完壁纸很糊」根因。
     * 方向感知后 inSampleSize 对两方向只按 cover 计算（只缩不扩），不反向降质。 */
    private fun wallpaperTargetBounds(): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * 保存前缩放策略：cover 到屏幕，只缩不扩，长边封顶 2×屏幕长边。
     * ⚠ 必须与 [WallpaperBackend.saveTargetSize] 逐条一致：共享层 save 先缩一次、encode 再判一次，
     * 策略须幂等（已处理图返回原尺寸）才不二次丢像素。
     */
    private fun scaleWallpaperForSave(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val cover = maxOf(
            targetW / bitmap.width.toFloat(),
            targetH / bitmap.height.toFloat()
        )
        // cover > 1：源图比屏幕小（短边方向覆盖不足），原样保存，显示侧放大是唯一选择
        if (cover > 1f) return bitmap
        val maxLong = 2 * maxOf(targetW, targetH)
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxLong) return bitmap
        val s = maxLong / longSide.toFloat()
        return bitmap.scale(
            (bitmap.width * s).toInt().coerceAtLeast(1),
            (bitmap.height * s).toInt().coerceAtLeast(1),
            true
        )
    }

    /** 计算满足目标尺寸的 2 的幂次降采样倍数（inSampleSize） */
    private fun calculateInSampleSize(bounds: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (bounds.outHeight > reqHeight || bounds.outWidth > reqWidth) {
            val halfHeight = bounds.outHeight / 2
            val halfWidth = bounds.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /** 缩放到屏幕分辨率后以 WebP 有损 80 存储 */
    fun saveWallpaper(bitmap: Bitmap): Boolean {
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            val scaled = scaleWallpaperForSave(bitmap, targetW, targetH)
            val file = File(appContext.filesDir, WALLPAPER_FILE)
            // API 30 起 WEBP 被 WEBP_LOSSY 取代，语义等价（均有损）
            val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                CompressFormat.WEBP
            }
            FileOutputStream(file).use { out -> scaled.compress(webpFormat, 80, out) }
            wallpaperCache.put(WALLPAPER_FILE, scaled)
            saveRawCache(scaled)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 带 Lru 缓存；解码按屏幕分辨率降采样 */
    fun loadWallpaper(): Bitmap? {
        wallpaperCache.get(WALLPAPER_FILE)?.let { return it }
        val file = File(appContext.filesDir, WALLPAPER_FILE)
        val rawCache = File(appContext.filesDir, WALLPAPER_RAW_CACHE)
        // 壁纸已被清掉（webp 没了）但像素缓存还在：先作废，否则会读回一张已经删掉的壁纸
        if (rawCache.exists() && !file.exists()) rawCache.delete()
        loadRawCache()?.let {
            wallpaperCache.put(WALLPAPER_FILE, it)
            return it
        }
        if (!file.exists()) return null
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            // 先读尺寸再降采样，避免超大图一次性解码耗尽内存
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            opts.inSampleSize = calculateInSampleSize(opts, targetW, targetH)
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, opts)?.also {
                wallpaperCache.put(WALLPAPER_FILE, it)
                saveRawCache(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── 原始像素缓存 ────────────────────────────────────────────
    /** 写原始像素缓存；失败静默（只影响下次启动速度，不影响正确性） */
    private fun saveRawCache(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0 || bitmap.byteCount != w * h * 4) return
        runCatching {
            val buffer = java.nio.ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)
            java.io.DataOutputStream(
                java.io.BufferedOutputStream(
                    FileOutputStream(File(appContext.filesDir, WALLPAPER_RAW_CACHE))
                )
            ).use { out ->
                out.writeInt(w)
                out.writeInt(h)
                out.write(buffer.array())
            }
        }
    }

    /** 读原始像素缓存；不存在/损坏/尺寸对不上都返回 null，由调用方回落到 webp */
    private fun loadRawCache(): Bitmap? = runCatching {
        val file = File(appContext.filesDir, WALLPAPER_RAW_CACHE)
        if (!file.exists()) return@runCatching null
        java.io.DataInputStream(
            java.io.BufferedInputStream(java.io.FileInputStream(file))
        ).use { input ->
            val w = input.readInt()
            val h = input.readInt()
            val size = w * h * 4
            if (w <= 0 || h <= 0 || size / 4 != w * h) return@runCatching null
            val bytes = ByteArray(size)
            input.readFully(bytes)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            if (bitmap.byteCount != size) {
                bitmap.recycle()
                return@runCatching null
            }
            bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
            bitmap
        }
    }.getOrNull()
}