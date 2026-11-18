package com.haooz.chedule.ui.components.slider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 滑块配色。
 *
 * 只由 [Color] 组成，可在 commonMain 声明并被各平台共用。
 *
 * @param activeTrack 已完成部分的轨道颜色。
 * @param inactiveTrack 未完成部分的轨道颜色。
 * @param thumb 滑块圆球颜色。
 * @param activeTick 已越过锚点的刻度颜色。
 * @param inactiveTick 未越过锚点的刻度颜色。
 * @param thumbShadow 圆球投影颜色。
 */
@Immutable
data class NexioSliderColors(
    val activeTrack: Color,
    val inactiveTrack: Color,
    val thumb: Color,
    val activeTick: Color,
    val inactiveTick: Color,
    val thumbShadow: Color,
)

/**
 * 滑块默认配色。
 *
 * 与 Miuix `SliderDefaults.sliderColors()` 取自同一套主题色，深浅色与禁用态都由
 * [MiuixTheme.colorScheme] 决定。
 */
object NexioSliderDefaults {

    /**
     * 构造默认配色。
     *
     * @param enabled 是否启用；禁用时取主题的 disabled 系列颜色。
     * @return 与 Miuix 滑块一致的配色。
     */
    @Composable
    fun colors(enabled: Boolean = true): NexioSliderColors {
        val scheme = MiuixTheme.colorScheme
        val dark = isAppDarkTheme()
        return NexioSliderColors(
            activeTrack = if (enabled) scheme.primary else scheme.disabledPrimarySlider,
            inactiveTrack = if (enabled) scheme.sliderBackground else scheme.disabledSecondary,
            thumb = if (enabled) scheme.onPrimary else scheme.disabledOnPrimary,
            activeTick = scheme.sliderKeyPointForeground,
            inactiveTick = scheme.sliderKeyPoint,
            thumbShadow = Color(if (dark) 0x40000000 else 0x1a000000),
        )
    }
}
