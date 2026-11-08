package com.haooz.softglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class GlassSurfaceAlphaDrop(val light: Float = 0f, val dark: Float = 0f)

/** 绘制期可读的主题环境，避免悬浮层 uniform 捕获组合期的旧值。 */
object GlassEnvironment {
    var isDark: Boolean by mutableStateOf(false)
        internal set
    var hasWallpaper: Boolean by mutableStateOf(true)
        internal set

    fun update(isDark: Boolean = this.isDark, hasWallpaper: Boolean = this.hasWallpaper) {
        this.isDark = isDark
        this.hasWallpaper = hasWallpaper
    }
}

val LocalGlassSpec = compositionLocalOf { GlassPreset.Nexio }
val LocalGlassMaterialLevel = compositionLocalOf { GlassMaterialLevel.SOFT }
val LocalGlassTuning = compositionLocalOf { GlassMaterialLevel.SOFT.tuning() }
val LocalGlassIsDark = compositionLocalOf { false }
val LocalMaterialNoneEnabled = compositionLocalOf { false }
val LocalGlassBlurDp = compositionLocalOf { 6f }
val LocalGlassPanelBlurDp = compositionLocalOf { 12f }
val LocalGlassDialogBlurDp = compositionLocalOf { 12f }
val LocalGlassSurfaceAlphaDrop = compositionLocalOf {
    GlassSurfaceAlphaDrop(0.2f, 0.32f)
}
val LocalGlassEffectsEnabled = compositionLocalOf { true }
val LocalGlassRedrawSignal = staticCompositionLocalOf<() -> Unit> { {} }

/** 一次注入档位、色调与全部降级口径。 */
@Composable
fun SoftGlassTheme(
    preset: GlassSpec = GlassPreset.Nexio,
    level: GlassMaterialLevel = GlassMaterialLevel.SOFT,
    isDark: Boolean,
    hasWallpaper: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tuning = level.tuning()
    SideEffect {
        GlassEnvironment.isDark = isDark
        GlassEnvironment.hasWallpaper = hasWallpaper
    }
    CompositionLocalProvider(
        LocalGlassSpec provides preset,
        LocalGlassMaterialLevel provides level,
        LocalGlassTuning provides tuning,
        LocalGlassIsDark provides isDark,
        LocalMaterialNoneEnabled provides (level == GlassMaterialLevel.NONE),
        LocalGlassBlurDp provides tuning.blurDp,
        LocalGlassPanelBlurDp provides tuning.panelBlurDp,
        LocalGlassDialogBlurDp provides tuning.dialogBlurDp,
        LocalGlassSurfaceAlphaDrop provides GlassSurfaceAlphaDrop(
            tuning.surfaceAlphaDropLight,
            tuning.surfaceAlphaDropDark,
        ),
        LocalGlassEffectsEnabled provides tuning.glassEffectsEnabled,
        content = content,
    )
}
