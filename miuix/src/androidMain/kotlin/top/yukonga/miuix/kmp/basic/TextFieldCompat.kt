/**
 * TextView / Typeface 的高版本 API 兼容层（minSdk 26）。
 *
 * - `Typeface.create(Typeface, int, boolean)`（带 weight 的重载）需要 API 28；
 *   低版本回退到经典的 `Typeface.create(Typeface, int style)`（BOLD/ITALIC）。
 * - `textCursorDrawable` / `textSelectHandle*` 需要 API 29；低版本取不到，
 *   直接跳过着色（用系统默认光标/手柄颜色），不影响输入功能。
 */
package top.yukonga.miuix.kmp.basic

import android.graphics.Typeface
import android.os.Build
import android.widget.TextView

private val supportsWeightedTypeface: Boolean
    get() = Build.VERSION.SDK_INT >= 28

private val supportsTextDrawables: Boolean
    get() = Build.VERSION.SDK_INT >= 29

/**
 * 按 fontWeight(1..1000) 设置 [TextView.typeface]，API 28 以下退化为
 * 传统 BOLD/ITALIC 档位。
 */
internal fun TextView.applyWeightedTypeface(base: Typeface?, weight: Int) {
    typeface = if (supportsWeightedTypeface) {
        Typeface.create(base, weight, false)
    } else {
        Typeface.create(base, legacyStyleForWeight(weight))
    }
}

/** fontWeight(1..1000) → 传统 Typeface style 位（原调用固定非斜体，这里只还原粗细档）。 */
private fun legacyStyleForWeight(weight: Int): Int =
    if (weight >= 600) Typeface.BOLD else Typeface.NORMAL

/** 给系统光标与选择手柄统一着色（API 29+；低版本静默跳过）。 */
internal fun TextView.applySelectionHandlesTint(color: Int) {
    if (!supportsTextDrawables) return
    runCatching { textCursorDrawable?.setTint(color) }
    runCatching { textSelectHandle?.setTint(color) }
    runCatching { textSelectHandleLeft?.setTint(color) }
    runCatching { textSelectHandleRight?.setTint(color) }
}