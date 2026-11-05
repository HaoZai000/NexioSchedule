package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.MiscStr
import com.haooz.chedule.ui.glass.BrightTone
import com.haooz.chedule.ui.glass.GlassFill
import com.haooz.chedule.ui.glass.GlassTone
import com.haooz.chedule.ui.glass.SoftGlassStyle
import com.haooz.chedule.ui.glass.ThemeFill
import com.kyant.backdrop.isRenderEffectSupported

/**
 * 应用材质质量档（通透玻璃 / 清透磨砂 / 无材质），玻璃模糊、折射、边光都读它；默认清透磨砂。
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

    /** 降级描边同色系提亮幅度（向白混合比例） */
    private const val STROKE_LIGHTEN = 0.2f

    /** 玻璃件采样模糊（dp）：全档位统一（无材质档为 0） */
    private const val GLASS_BLUR_DP = 6f
    /** 通透玻璃档下拉面板模糊（dp）：恒定值，不随展开渐变 */
    private const val GLASS_PANEL_BLUR_BEST = 4f
    /** 下拉面板比小玻璃件多出来的模糊量（dp）：8 → 24 的原始跨度（仅非通透玻璃档用） */
    private const val GLASS_PANEL_BLUR_SPAN = 16f
    /** 通透玻璃档玻璃底色 alpha 降幅：深色降得多一点，浅色降少一点（浅底本身已亮） */
    private const val GLASS_SURFACE_ALPHA_DROP_DARK = 0.32f
    private const val GLASS_SURFACE_ALPHA_DROP_LIGHT = 0.2f
    /** 通透玻璃档对话框模糊（dp） */
    private const val GLASS_DIALOG_BLUR_BEST = 6f
    /** 其余档对话框模糊（dp） */
    private const val GLASS_DIALOG_BLUR_MILLED = 24f

    // Pair.first 是持久化数据键（BEST/BALANCED/...），Pair.second 是展示名；按当前语言取值，不缓存
    val entries: List<Pair<String, String>>
        get() = listOf(
            BEST to MiscStr.materialBest(),
            BALANCED to MiscStr.materialBalanced(),
            NONE to MiscStr.materialNone(),
        )

    /** 当前档位；组合期可读，写入走 [apply]。 */
    var level: String by mutableStateOf(BALANCED)

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
        entries.firstOrNull { it.first == level }?.second ?: MiscStr.materialBalanced()

    /** 按钮 / 低栏背景板折射：仅通透玻璃档保留，其余档关闭。 */
    fun chromeLensEnabled(): Boolean = level == BEST

    /** 是否通透玻璃档（折射 + 低模糊那一档）。 */
    fun isBestTier(): Boolean = level == BEST

    /**
     * 悬浮控件（顶栏按钮、胶囊这类）的玻璃样式：基准样式套上基调与填充，再按材质档位
     * 决定折射与高光 —— 磨砂档没有折射，方向光也关掉（纯磨砂面）；通透玻璃档两者都留。
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

    /**
     * 玻璃件采样模糊半径（dp）。**所有档位统一 6**：档位差异交给折射与描边，
     * 不再靠模糊量区分（4/8 两档在小控件上几乎看不出差别，反而让参数多一套）。
     */
    fun glassBlurDp(): Float = if (level == NONE) 0f else GLASS_BLUR_DP

    /**
     * 弹出面板（下拉菜单）采样模糊半径（dp）。
     * 通透玻璃档取恒定值、**不随展开渐变**（折射已经做出「厚度」，再叠模糊只会糊掉边缘）；
     * 其余档取展开态的 24（小玻璃件 8 + 16 跨度）；无材质档 0（不模糊）。
     */
    fun glassPanelBlurDp(): Float = when (level) {
        NONE -> 0f
        BEST -> GLASS_PANEL_BLUR_BEST
        else -> glassBlurDp() + GLASS_PANEL_BLUR_SPAN
    }

    /** 对话框 / 贴底弹窗玻璃的采样模糊半径（dp）：通透玻璃档更薄，其余档维持 24，无材质档 0。 */
    fun glassDialogBlurDp(): Float = when (level) {
        NONE -> 0f
        BEST -> GLASS_DIALOG_BLUR_BEST
        else -> GLASS_DIALOG_BLUR_MILLED
    }

    /**
     * 玻璃底色 alpha：无材质档返回 1f（玻璃面改实色），通透玻璃档比常态低一档让折射和背后的内容
     * 透出来；清透磨砂档原值返回。
     *
     * @param milledAlpha 清透磨砂档原本使用的 alpha
     */
    fun glassSurfaceAlpha(milledAlpha: Float, isLight: Boolean): Float =
        if (level == NONE) 1f else (milledAlpha - glassSurfaceAlphaDrop(isLight)).coerceAtLeast(0f)

    /** 无材质档玻璃面实色化：半透明底色换成不透明（[baseAlpha] 其余档原样返回）。 */
    fun solidSurfaceAlpha(baseAlpha: Float): Float = if (level == NONE) 1f else baseAlpha

    /**
     * 通透玻璃档的底色 alpha 降幅（浅色 / 深色各一档）；清透磨砂 / 无材质档 0。
     * [glassSurfaceAlpha] 与 :miuix 下发都用它。
     */
    fun glassSurfaceAlphaDrop(isLight: Boolean): Float = when {
        level != BEST -> 0f
        isLight -> GLASS_SURFACE_ALPHA_DROP_LIGHT
        else -> GLASS_SURFACE_ALPHA_DROP_DARK
    }

    /**
     * 顶栏是否走「假渐进模糊」（等值 blur + alpha 淡出，不跑 AGSL 多重采样）。
     * 由「真·渐进模糊」开关决定，与材质档位无关；无材质档另有渐变遮罩优先。
     */
    fun progressiveBlurUseFake(): Boolean = !trueProgressiveBlur

    /**
     * 无材质档把高光描边降级为普通纯色描边（无模糊、SrcOver）；[baseColor] 非空时按同色系
     * 提亮一档作描边，否则按主题回退（浅色白 / 深色灰）。通透玻璃 / 清透磨砂保留原高光。
     */
    fun resolveEdgeLight(
        source: com.kyant.backdrop.edgelight.EdgeLight,
        isLightTheme: Boolean,
        baseColor: Color? = null,
    ): com.kyant.backdrop.edgelight.EdgeLight {
        if (level != NONE) return source
        val stroke = baseColor?.let(::lightenSameHue)
            ?: if (isLightTheme) Color.White else Color(0xFF333333)
        return com.kyant.backdrop.edgelight.EdgeLight(
            width = 0.8.dp,
            blurRadius = 0.dp,
            intensity = source.intensity,
            style = com.kyant.backdrop.edgelight.EdgeLightStyle.Uniform(
                color = stroke,
                blendMode = BlendMode.SrcOver,
            ),
        )
    }

    /** 同色系提亮一档：丢弃原 alpha 按不透明底色向白混合 [STROKE_LIGHTEN]，白底仍是白 */
    private fun lightenSameHue(color: Color): Color {
        val t = STROKE_LIGHTEN
        return Color(
            red = color.red + (1f - color.red) * t,
            green = color.green + (1f - color.green) * t,
            blue = color.blue + (1f - color.blue) * t,
            alpha = 1f,
        )
    }

    fun apply(level: String) {
        this.level = normalize(level)
    }

    /** 切换「真·渐进模糊」：全局状态立即更新，Theme 的 CompositionLocal 随重组下发。 */
    fun applyTrueProgressiveBlur(enabled: Boolean) {
        trueProgressiveBlur = enabled
    }

    /**
     * 启动时从偏好载入到全局状态，走 [AppStorage] 按名字取（Android 仍是同一个
     * `app_preferences` 文件，存量数据不受影响）。旧「性能优先」档值由 [normalize] 回落到清透磨砂。
     */
    fun load() {
        val store = AppStorage.store(PREFS_NAME)
        level = normalize(store.getString(KEY_APP_MATERIAL, BALANCED))
        trueProgressiveBlur = store.getBoolean(KEY_TRUE_PROGRESSIVE_BLUR, true)
    }

    /** 旧值 / 非法值（含已下线的 "performance"）落到清透磨砂档；模糊能力不可用（Android API < 31）一律锁进无材质档。 */
    private fun normalize(level: String): String {
        lockedLevel?.let { return it }
        val resolved = when (level) {
            BEST, BALANCED, NONE -> level
            else -> BALANCED
        }
        return if (resolved != NONE && !isRenderEffectSupported()) NONE else resolved
    }
}
