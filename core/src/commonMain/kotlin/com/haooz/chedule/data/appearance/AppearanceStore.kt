package com.haooz.chedule.data.appearance

import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KeyValueStore
import com.haooz.chedule.data.storage.getStringOrNull

/**
 * 外观存储（跨平台）：键名、快照编解码与缓存。getter/setter 与宿主同名，调用点不用改。
 * ⚠ 键名与文件位置是数据兼容契约（[AppearancePrefs.FILE] / [AppearancePrefs.FILE_STYLE_KEY]），
 * 改了等于老用户外观读不出来。
 */
object AppearanceStore {

    /** 独立 prefs 文件名（与 [AppearancePrefs.FILE] 同值） */
    private const val FILE = AppearancePrefs.FILE

    /** 唯一的数据键：参数 JSON 快照 */
    private const val FILE_STYLE_KEY = AppearancePrefs.FILE_STYLE_KEY

    /** 快照缓存，读多写少 */
    private var cached: AppearanceStyle? = null

    /**
     * 首帧兜底配置：搭配（壁纸位图 + 参数）还没加载完时由界面侧读它。
     *
     * 从盘上快照同步派生，与搭配加载同源，所以兜底值恒等于加载结果、不会跳变。
     * 不要改成「等外部写入的 var」：没人写就会先画默认卡片高度 / 模糊，加载完再跳一次。
     */
    val cachedConfig: AppearanceConfig
        get() = AppearanceConfig.fromStyle(currentStyle())

    /** 是否处于 [batchSave] 块内 */
    private var inBatch = false

    /** 批量期间是否有过写入（决定块末要不要落盘） */
    private var batchDirty = false

    private fun store(): KeyValueStore = AppStorage.store(FILE)

    /** 当前生效的外观快照。盘上没有 / 解析失败时回落默认值。 */
    fun currentStyle(): AppearanceStyle {
        cached?.let { return it }
        val raw = runCatching { store().getStringOrNull(FILE_STYLE_KEY) }.getOrNull()
        val style = raw?.let { AppearanceStyleCodec.decodeOrNull(it) } ?: AppearanceStyle.defaults()
        cached = style
        return style
    }

    /** 覆盖写入快照。批量期间只更新内存，等块末一次性落盘。 */
    fun saveStyle(style: AppearanceStyle) {
        cached = style
        if (inBatch) {
            batchDirty = true
            return
        }
        flush()
    }

    /** 在现有快照基础上做一次变更并落盘 */
    fun updateStyle(transform: (AppearanceStyle) -> AppearanceStyle) {
        saveStyle(transform(currentStyle()))
    }

    /**
     * 批量保存：块内写入合并为一次磁盘提交（一次拖动会触发十几次写入），可嵌套，由最外层落盘。
     */
    fun batchSave(block: () -> Unit) {
        if (inBatch) {
            block()
            return
        }
        inBatch = true
        batchDirty = false
        try {
            block()
        } finally {
            inBatch = false
            val dirty = batchDirty
            batchDirty = false
            if (dirty) flush()
        }
    }

    /** 把内存里的快照写到盘上。 */
    private fun flush() {
        val style = cached ?: return
        store().edit { putString(FILE_STYLE_KEY, AppearanceStyleCodec.encode(style)) }
    }

    /** 让缓存失效（盘上数据被别处改过时用）。 */
    fun invalidate() {
        cached = null
    }

    // ── 便捷读写（与宿主同名，调用点不用改） ──

    fun getOffsetX(): Float = currentStyle().offsetX
    fun getOffsetY(): Float = currentStyle().offsetY
    fun getScale(): Float = currentStyle().scale
    fun getOffsetRefW(): Float = currentStyle().offsetRefW
    fun getOffsetRefH(): Float = currentStyle().offsetRefH

    fun saveState(offsetX: Float, offsetY: Float, scale: Float, refW: Float = 0f, refH: Float = 0f) =
        updateStyle { it.copy(offsetX = offsetX, offsetY = offsetY, scale = scale, offsetRefW = refW, offsetRefH = refH) }

    fun getCardBlur(): Float = currentStyle().cardBlur
    fun saveCardBlur(radius: Float) = updateStyle { it.copy(cardBlur = radius) }

    fun getCardAlpha(): Float = currentStyle().cardAlpha
    fun saveCardAlpha(alpha: Float) = updateStyle { it.copy(cardAlpha = alpha) }

    fun getCardSurfaceAlpha(): Float = currentStyle().safeCardSurfaceAlpha
    fun saveCardSurfaceAlpha(alpha: Float) = updateStyle { it.copy(cardSurfaceAlpha = alpha) }

    fun getCardHeight(): Float = currentStyle().safeCardHeight
    fun saveCardHeight(height: Float) = updateStyle { it.copy(cardHeight = height) }

    fun getCardCornerRadius(): Float = currentStyle().cardCornerRadius
    fun saveCardCornerRadius(radius: Float) = updateStyle { it.copy(cardCornerRadius = radius) }

    fun getWallpaperBrightness(): Float = currentStyle().wallpaperBrightness
    fun saveWallpaperBrightness(brightness: Float) = updateStyle { it.copy(wallpaperBrightness = brightness) }

    fun getWallpaperIsLight(): Boolean? = currentStyle().wallpaperIsLight
    fun saveWallpaperIsLight(isLight: Boolean?) = updateStyle { it.copy(wallpaperIsLight = isLight) }

    fun getShowBreakDividers(): Boolean = currentStyle().showBreakDividers
    fun saveShowBreakDividers(show: Boolean) = updateStyle { it.copy(showBreakDividers = show) }

    fun getShowWeekGridLines(): Boolean = currentStyle().showWeekGridLines
    fun saveShowWeekGridLines(show: Boolean) = updateStyle { it.copy(showWeekGridLines = show) }

    fun getCardContentAlignment(): CardContentAlignment = currentStyle().safeAlignment
    fun saveCardContentAlignment(alignment: CardContentAlignment) =
        updateStyle { it.copy(cardContentAlignment = alignment) }

    fun getCardTextColor(): CardTextColor = currentStyle().safeTextColor
    fun saveCardTextColor(color: CardTextColor) = updateStyle { it.copy(cardTextColor = color) }

    fun getCardTextScale(): Float = currentStyle().safeCardTextScale
    fun saveCardTextScale(scale: Float) = updateStyle { it.copy(cardTextScale = scale) }

    fun getShowClassroom(): Boolean = currentStyle().showClassroom
    fun saveShowClassroom(show: Boolean) = updateStyle { it.copy(showClassroom = show) }

    fun getShowTeacher(): Boolean = currentStyle().showTeacher
    fun saveShowTeacher(show: Boolean) = updateStyle { it.copy(showTeacher = show) }

    fun getCardRefraction(): CardRefractionLevel = currentStyle().safeRefraction
    fun saveCardRefraction(level: CardRefractionLevel) = updateStyle { it.copy(cardRefraction = level) }

    fun getCardEdgeThickness(): CardEdgeThickness = currentStyle().safeEdgeThickness
    fun saveCardEdgeThickness(level: CardEdgeThickness) = updateStyle { it.copy(cardEdgeThickness = level) }

    fun getCardRim(): Boolean = currentStyle().cardRim
    fun saveCardRim(enabled: Boolean) = updateStyle { it.copy(cardRim = enabled) }

    fun getWallpaperBlur(): Boolean = currentStyle().wallpaperBlur
    fun saveWallpaperBlur(blur: Boolean) = updateStyle { it.copy(wallpaperBlur = blur) }
}
