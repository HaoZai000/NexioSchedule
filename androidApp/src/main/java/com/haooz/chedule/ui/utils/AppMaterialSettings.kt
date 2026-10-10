package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.AppStorage

/**
 * 应用材质质量档（应用偏好设置中可切换，默认最佳）。
 *
 * 最佳 / 均衡 / 性能 —— 供玻璃模糊、折射、边光等材质效果读取。默认均衡。
 */
object AppMaterialSettings {

    const val KEY_APP_MATERIAL = "app_material"
    private const val PREFS_NAME = "app_preferences"

    const val BEST = "best"
    const val BALANCED = "balanced"
    const val PERFORMANCE = "performance"

    /** 性能档描边同色系提亮幅度（向白混合比例） */
    private const val STROKE_LIGHTEN = 0.2f

    val entries: List<Pair<String, String>> = listOf(
        BEST to "最佳",
        BALANCED to "均衡",
        PERFORMANCE to "性能",
    )

    /** 当前档位；组合期可读，写入走 [apply]。 */
    var level: String by mutableStateOf(BALANCED)

    fun labelOf(level: String): String =
        entries.firstOrNull { it.first == level }?.second ?: "均衡"

    /**
     * 按钮 / 低栏背景板折射。
     * 最佳保留；均衡及更省档关闭。
     */
    fun chromeLensEnabled(): Boolean = level == BEST

    /**
     * 性能档：假渐进模糊（等值 blur + alpha 淡出）替代真渐进（AGSL 多重采样）。
     * 最佳 / 均衡保留真模糊。
     */
    fun progressiveBlurUseFake(): Boolean = level == PERFORMANCE

    /**
     * 性能档：高光描边降级为普通纯色描边（无模糊、SrcOver）。
     * 传入底板色 [baseColor] 时按同色系提亮一档作描边，彩色按钮得到同色高光；
     * 未传时按主题回退（浅色白 / 深色灰）。最佳 / 均衡保留原高光。
     */
    fun resolveEdgeLight(
        source: com.kyant.backdrop.edgelight.EdgeLight,
        isLightTheme: Boolean,
        baseColor: Color? = null,
    ): com.kyant.backdrop.edgelight.EdgeLight {
        if (level != PERFORMANCE) return source
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

    /**
     * 启动时从偏好载入到全局状态。
     *
     * 不再需要 `Context`：走 [AppStorage] 按名字取，平台实现由启动时注入
     * （Android 仍是同一个 `app_preferences` 文件，存量数据不受影响）。
     */
    fun load() {
        level = normalize(AppStorage.store(PREFS_NAME).getString(KEY_APP_MATERIAL, BALANCED))
    }

    /** 旧值 / 非法值落到均衡档。 */
    private fun normalize(level: String): String = when (level) {
        BEST, BALANCED, PERFORMANCE -> level
        else -> BALANCED
    }
}
