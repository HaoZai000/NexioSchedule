package com.haooz.chedule.ui.utils

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

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
     * 性能档：高光描边降级为普通纯色描边（浅色白 / 深色灰，无模糊、SrcOver）。
     * 最佳 / 均衡保留原高光。
     */
    fun resolveEdgeLight(
        source: com.haooz.chedule.ui.effects.edgelight.EdgeLight,
        isLightTheme: Boolean,
    ): com.haooz.chedule.ui.effects.edgelight.EdgeLight {
        if (level != PERFORMANCE) return source
        val stroke =
            if (isLightTheme) androidx.compose.ui.graphics.Color.White
            else androidx.compose.ui.graphics.Color(0xFF333333)
        return com.haooz.chedule.ui.effects.edgelight.EdgeLight(
            width = 0.5.dp,
            blurRadius = 0.dp,
            intensity = source.intensity,
            style = com.haooz.chedule.ui.effects.edgelight.EdgeLightStyle.Uniform(
                color = stroke,
                blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver,
            ),
        )
    }

    fun apply(level: String) {
        this.level = normalize(level)
    }

    /** 启动时从偏好载入到全局状态。 */
    fun load(context: Context) {
        level = normalize(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_APP_MATERIAL, BALANCED) ?: BALANCED
        )
    }

    /** 旧值 / 非法值落到均衡档。 */
    private fun normalize(level: String): String = when (level) {
        BEST, BALANCED, PERFORMANCE -> level
        else -> BALANCED
    }
}
