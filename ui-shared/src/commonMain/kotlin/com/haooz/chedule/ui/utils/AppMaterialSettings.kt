package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.MiscStr
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.GlassFill
import com.haooz.softglass.glass.GlassTone
import com.haooz.softglass.glass.SoftGlassStyle
import com.haooz.softglass.glass.ThemeFill
import com.haooz.softglass.GlassMaterialLevel
import com.haooz.softglass.surfaceAlpha
import com.haooz.softglass.tuning
import com.haooz.softglass.platform.isRenderEffectSupported

/**
 * 应用材质质量档（柔光玻璃 / 清透磨砂 / 无材质），玻璃模糊、折射、边光都读它；默认柔光玻璃。
 * 顶栏「真·渐进模糊」是独立开关 [trueProgressiveBlur]，不占用档位。
 *
 * 统一限制：RenderEffect 不可用（Android API < 31）的设备锁死无材质档（见 [normalize]），
 * 模糊相关的按能力降级分支不在这里出现——能力不足时档位本身就是无材质。
 */
object AppMaterialSettings {

    const val KEY_APP_MATERIAL = "app_material"
    /** 顶栏「真·渐进模糊」开关的持久化键（默认开启）。 */
    const val KEY_TRUE_PROGRESSIVE_BLUR = "true_progressive_blur"
    private const val PREFS_NAME = "app_preferences"

    const val BEST = "best"
    const val BALANCED = "balanced"
    const val NONE = "none"

    private fun materialLevel(): GlassMaterialLevel = when (level) {
        BEST -> GlassMaterialLevel.SOFT
        NONE -> GlassMaterialLevel.NONE
        else -> GlassMaterialLevel.FROSTED
    }

    // Pair.first 是持久化数据键（BEST/BALANCED/...），Pair.second 是展示名；按当前语言取值，不缓存
    val entries: List<Pair<String, String>>
        get() = listOf(
            BEST to MiscStr.materialBest(),
            BALANCED to MiscStr.materialBalanced(),
            NONE to MiscStr.materialNone(),
        )

    /** 当前档位；组合期可读，写入走 [apply]。 */
    var level: String by mutableStateOf(BEST)

    /**
     * 顶栏「真·渐进模糊」开关（默认开启）。
     * 开 → AGSL 逐像素半径渐变的真渐进；关 → 等值 blur + alpha 淡出的轻量假渐进。
     * 无材质档走渐变遮罩，此开关对它无效（设置页会置灰）。
     * 组合期可读：Theme 下发 `progressiveBlurUseFake()` 时读它，切开关要当场重算。
     */
    var trueProgressiveBlur: Boolean by mutableStateOf(true)
        private set

    /**
     * 平台锁定的档位（null = 用户可选）。锁定后 [normalize] 一律返回它，
     * 设置页也不展示切换入口。鸿蒙锁 [BEST]：那边玻璃能力固定，不暴露档位。
     */
    var lockedLevel: String? = null
        private set

    fun lockTo(level: String) {
        lockedLevel = level
        this.level = level
    }

    fun labelOf(level: String): String =
        entries.firstOrNull { it.first == level }?.second ?: MiscStr.materialBest()

    /** 按钮 / 低栏背景板折射：仅柔光玻璃档保留，其余档关闭。 */
    fun chromeLensEnabled(): Boolean = level == BEST

    /** 是否柔光玻璃档（折射 + 低模糊那一档）。 */
    fun isBestTier(): Boolean = level == BEST

    /**
     * 悬浮控件（顶栏按钮、胶囊这类）的玻璃样式：基准样式套上基调与填充，再按材质档位
     * 决定折射与高光 —— 磨砂档没有折射，方向光也关掉（纯磨砂面）；柔光玻璃档两者都留。
     *
     * 控件的材质就这一处定义，调用方只传「用哪个基调 / 哪个填充」。
     */
    fun chromeGlassStyle(
        base: SoftGlassStyle,
        isDark: Boolean,
        fill: GlassFill = ThemeFill,
        tone: GlassTone = BrightTone,
    ): SoftGlassStyle {
        val resolved = base.copy(fill = fill, tone = tone).filled(isDark)
        return if (isBestTier()) {
            resolved
        } else {
            resolved.copy(refractionScale = 0f, edgeHighlight = false)
        }
    }

    /** 是否无材质档：玻璃模糊 / 折射 / vibrancy 全关，渐进模糊降级为渐变遮罩。 */
    fun isNoneTier(): Boolean = level == NONE

    /** 玻璃材质总开关（无材质档 false）：玻璃件的 vibrancy / 硬编码模糊与折射一并关闭。 */
    fun glassEffectsEnabled(): Boolean = level != NONE

    /** 玻璃件采样模糊半径（dp），档位口径由 :softglass 提供。 */
    fun glassBlurDp(): Float = materialLevel().tuning().blurDp

    /** 弹出面板展开态采样模糊半径（dp）。 */
    fun glassPanelBlurDp(): Float = materialLevel().tuning().panelBlurDp

    /** 对话框 / 贴底弹窗玻璃的采样模糊半径（dp）。 */
    fun glassDialogBlurDp(): Float = materialLevel().tuning().dialogBlurDp

    /** 玻璃底色 alpha，无材质档改实色。 */
    fun glassSurfaceAlpha(milledAlpha: Float, isLight: Boolean): Float =
        materialLevel().surfaceAlpha(milledAlpha, isLight)

    /** 无材质档玻璃面实色化：半透明底色换成不透明（[baseAlpha] 其余档原样返回）。 */
    fun solidSurfaceAlpha(baseAlpha: Float): Float = if (level == NONE) 1f else baseAlpha

    /** 柔光玻璃档的底色 alpha 降幅，[glassSurfaceAlpha] 与 :miuix 下发都用它。 */
    fun glassSurfaceAlphaDrop(isLight: Boolean): Float {
        val tuning = materialLevel().tuning()
        return if (isLight) tuning.surfaceAlphaDropLight else tuning.surfaceAlphaDropDark
    }

    /**
     * 顶栏是否走「假渐进模糊」（等值 blur + alpha 淡出，不跑 AGSL 多重采样）。
     * 由「真·渐进模糊」开关决定，与材质档位无关；无材质档另有渐变遮罩优先。
     */
    fun progressiveBlurUseFake(): Boolean = !trueProgressiveBlur



    fun apply(level: String) {
        this.level = normalize(level)
    }

    /** 切换「真·渐进模糊」：全局状态立即更新，Theme 的 CompositionLocal 随重组下发。 */
    fun applyTrueProgressiveBlur(enabled: Boolean) {
        trueProgressiveBlur = enabled
    }

    /**
     * 启动时从偏好载入到全局状态，走 [AppStorage] 按名字取（Android 仍是同一个
     * `app_preferences` 文件，存量数据不受影响）。旧「性能优先」档值由 [normalize] 回落到柔光玻璃。
     */
    fun load() {
        val store = AppStorage.store(PREFS_NAME)
        level = normalize(store.getString(KEY_APP_MATERIAL, BEST))
        trueProgressiveBlur = store.getBoolean(KEY_TRUE_PROGRESSIVE_BLUR, true)
    }

    /** 旧值 / 非法值（含已下线的 "performance"）落到柔光玻璃档；模糊能力不可用（Android API < 31）一律锁进无材质档。 */
    private fun normalize(level: String): String {
        lockedLevel?.let { return it }
        val resolved = when (level) {
            BEST, BALANCED, NONE -> level
            else -> BEST
        }
        return if (resolved != NONE && !isRenderEffectSupported()) NONE else resolved
    }
}
