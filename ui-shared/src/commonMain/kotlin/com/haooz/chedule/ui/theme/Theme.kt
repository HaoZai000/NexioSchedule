/** 应用主题 - 定义 Material3 主题配色方案与 NexioUI 主题作用域 */
package com.haooz.chedule.ui.theme

import com.haooz.softglass.GlassEnvironment
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import com.haooz.chedule.data.platform.currentDeviceInfo
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KEY_CHANGED_ANYWHERE
import com.haooz.chedule.ui.utils.APP_THEME_PREFS_FILE
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.KEY_THEME_MODE
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.THEME_MODE_SYSTEM
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.material.NexioMaterialLevel
import com.haooz.nexioui.material.LocalMaterialNoneEnabled as NexioLocalMaterialNoneEnabled
import com.haooz.nexioui.material.LocalUseFakeProgressiveBlur as NexioLocalUseFakeProgressiveBlur
import com.haooz.nexioui.indication.NexioIndication
import com.haooz.nexioui.navigationback.LocalNexioPredictiveBackEnabled
import com.haooz.nexioui.theme.NexioColorScheme
import com.haooz.nexioui.theme.NexioDarkColorScheme
import com.haooz.nexioui.theme.NexioLightColorScheme
import com.haooz.nexioui.theme.NexioTheme

/** 应用触感反馈开关对应的 SharedPreferences 文件与键 */
const val APP_PREFS_NAME = "app_preferences"
const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
const val KEY_MONET_COLOR = "monet_color_enabled"

/** 系统壁纸取色目前仅由 Android 12 及以上提供，其他宿主保留原有主题。 */
internal fun isMonetColorAvailable(): Boolean = currentDeviceInfo().sdkLevel >= 31

@Composable
internal expect fun platformMonetMaterialColors(dark: Boolean): ColorScheme

/** 系统壁纸取色（Monet）是否生效。 */
val LocalMonetColorEnabled = staticCompositionLocalOf { false }

/** 一处主题作用域解析出的配色。 */
@Immutable
data class AppThemeColors(
    val isDark: Boolean,
    val isDynamicColor: Boolean,
    val colorScheme: NexioColorScheme,
)

/**
 * 解析页面 / 弹窗作用域的主题配色。
 *
 * 非动态取色时直接使用 NexioUI 的亮 / 暗色板；动态取色时把平台 Monet 色板
 * 映射成 [NexioColorScheme]，映射规则与旧实现保持一致。
 */
@Composable
fun rememberAppThemeColors(
    isDark: Boolean? = null,
    themeMode: String = THEME_MODE_SYSTEM,
    // 传原始偏好值时本函数自行叠加平台判断；不传则吃到当前作用域已生效的取值。
    monetEnabled: Boolean = LocalMonetColorEnabled.current,
): AppThemeColors {
    val dark = isDark ?: when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val useMonet = monetEnabled && isMonetColorAvailable()
    val monetScheme = if (useMonet) platformMonetMaterialColors(dark) else null
    val scheme = when {
        monetScheme != null -> monetScheme.toNexioColorScheme()
        dark -> NexioDarkColorScheme
        else -> NexioLightColorScheme
    }
    return remember(dark, useMonet, scheme) {
        AppThemeColors(isDark = dark, isDynamicColor = useMonet, colorScheme = scheme)
    }
}

/** 在一处局部作用域里切换 NexioUI 主题（替代原先按 controller 包一层 MiuixTheme）。 */
@Composable
fun AppNexioTheme(
    colors: AppThemeColors,
    content: @Composable () -> Unit,
) {
    NexioTheme(
        isDark = colors.isDark,
        colorScheme = colors.colorScheme,
        isDynamicColor = colors.isDynamicColor,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.colorScheme.onBackground,
            content = content,
        )
    }
}

/** Unmodified system appearance; null lets hosts without a bridge use Compose's fallback. */
val LocalSystemDarkTheme = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun CourseScheduleTheme(
    systemDarkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val prefs = remember { AppStorage.store(APP_THEME_PREFS_FILE) }
    val themeMode = remember { mutableStateOf(prefs.getString(KEY_THEME_MODE, THEME_MODE_SYSTEM)) }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_MONET_COLOR, false)) }

    // 全局触感开关：关闭后屏蔽全 App 触感
    val hapticPrefs = remember { AppStorage.store(APP_PREFS_NAME) }
    var hapticFeedbackEnabled by remember {
        mutableStateOf(hapticPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
    }

    DisposableEffect(prefs) {
        // ⚠ iOS 回调不带 key（传 `KEY_CHANGED_ANYWHERE` 哨兵），Android 才给准确 key。
        // 不能用 `key == KEY_THEME_MODE` 精确过滤：那样 iOS 收到通知也会被丢掉、主题不刷新。
        // key 未知时无条件复查，已知时才精确过滤。
        val token = prefs.registerListener { key ->
            if (key == KEY_CHANGED_ANYWHERE || key == KEY_THEME_MODE) {
                themeMode.value = prefs.getString(KEY_THEME_MODE, THEME_MODE_SYSTEM)
            }
            if (key == KEY_CHANGED_ANYWHERE || key == KEY_MONET_COLOR) {
                monetEnabled = prefs.getBoolean(KEY_MONET_COLOR, false)
            }
        }
        onDispose {
            prefs.unregisterListener(token)
        }
    }

    DisposableEffect(hapticPrefs) {
        // 同上：iOS 传哨兵 → 无条件复查，不能按 key 精确过滤。
        val token = hapticPrefs.registerListener { key ->
            if (key == KEY_CHANGED_ANYWHERE || key == KEY_HAPTIC_FEEDBACK) {
                hapticFeedbackEnabled = hapticPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
            }
        }
        onDispose {
            hapticPrefs.unregisterListener(token)
        }
    }

    // iOS supplies the system appearance from outside the overridden UIKit subtree.
    val appDark = when (themeMode.value) {
        "light" -> false
        "dark" -> true
        else -> systemDarkTheme
    }
    val colors = rememberAppThemeColors(isDark = appDark, monetEnabled = monetEnabled)
    val materialColors = if (monetEnabled && isMonetColorAvailable()) {
        platformMonetMaterialColors(appDark ?: isSystemInDarkTheme())
    } else MaterialTheme.colorScheme
    // 捕获当前（系统默认）触感实现，封装为受开关控制的门控实现
    val defaultHaptic = LocalHapticFeedback.current
    val gatedHaptic = remember(defaultHaptic) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackEnabled) {
                    defaultHaptic.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    val density = LocalDensity.current
    // GlassEnvironment 是 draw 期的只读镜像：
    // isDark 供按压高光 / 阴影这类少量观感读取，density 供液态 lens 的 dp→px 换算；
    // 全应用唯一写入点，不再由上百个 isAppDarkTheme() 调用点用 SideEffect 竞争写。
    val effectiveGlassDark = appDark ?: isSystemInDarkTheme()
    SideEffect { GlassEnvironment.update(isDark = effectiveGlassDark, density = density.density) }
    val appMaterialLevel = AppMaterialSettings.level
    SideEffect {
        NexioMaterial.level = when (appMaterialLevel) {
            AppMaterialSettings.LIQUID -> NexioMaterialLevel.Liquid
            AppMaterialSettings.BALANCED -> NexioMaterialLevel.Frosted
            AppMaterialSettings.NONE -> NexioMaterialLevel.None
            else -> NexioMaterialLevel.Soft
        }
    }
    val outerTextStyle: TextStyle = LocalTextStyle.current
    CompositionLocalProvider(
        LocalHapticFeedback provides gatedHaptic,
        LocalMonetColorEnabled provides (monetEnabled && isMonetColorAvailable()),
        LocalSystemDarkTheme provides systemDarkTheme,
        NexioLocalMaterialNoneEnabled provides AppMaterialSettings.isNoneTier(),
        NexioLocalUseFakeProgressiveBlur provides AppMaterialSettings.progressiveBlurUseFake(),
        LocalNexioPredictiveBackEnabled provides PredictiveBackSettings.enabled,
    ) {
        AppNexioTheme(colors) {
            MaterialTheme(colorScheme = materialColors) {
                // MaterialTheme 会把 LocalIndication 换成 M3 水波纹；主题内再覆盖回
                // NexioIndication，保证 Modifier.clickable 与 miuix 时期一样是整块淡色反馈。
                val indicationColor = NexioTheme.colorScheme.onBackground
                val indication = remember(indicationColor) { NexioIndication(color = indicationColor) }
                CompositionLocalProvider(
                    LocalIndication provides indication,
                    LocalTextStyle provides outerTextStyle,
                ) {
                    content()
                }
            }
        }
    }
}

// ── Monet 色板 → NexioColorScheme ──────────────────────────────────────
// 动态取色时 Material3 只给基础角色，其余语义色按旧实现的规则推导，
// 保证开启 / 关闭 Monet 时控件观感一致。

/** 前景色叠在背景色上并返回不透明结果。 */
private fun opaqueOver(fg: Color, bg: Color): Color {
    val fa = fg.alpha
    val ba = bg.alpha
    val outA = fa + ba * (1f - fa)
    if (outA == 0f) return Color(0f, 0f, 0f, 0f)
    val r = (fg.red * fa + bg.red * ba * (1f - fa)) / outA
    val g = (fg.green * fa + bg.green * ba * (1f - fa)) / outA
    val b = (fg.blue * fa + bg.blue * ba * (1f - fa)) / outA
    return Color(r, g, b, outA)
}

private fun ensureOpaqueOver(fg: Color, bg: Color): Color =
    if (fg.alpha >= 1f) fg else opaqueOver(fg, bg).copy(alpha = 1f)

private fun ColorScheme.toNexioColorScheme(): NexioColorScheme {
    val baseSurface = surface
    val onSurfaceSecondary = ensureOpaqueOver(onSurface.copy(alpha = 0.8f), baseSurface)
    val onSurfaceContainerHigh = ensureOpaqueOver(onSurface.copy(alpha = 0.8f), surfaceContainerHigh)
    val disabledSecondaryVariant = ensureOpaqueOver(surfaceContainerHigh.copy(alpha = 0.6f), baseSurface)
    return NexioColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        onSurfaceVariantSummary = onSurfaceVariant,
        onSurfaceVariantActions = onSurfaceVariant,
        outline = outline,
        dividerLine = outlineVariant,
        secondary = outlineVariant,
        onSecondary = outline,
        onSecondaryVariant = onSurface,
        disabledSecondaryVariant = disabledSecondaryVariant,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        secondaryVariant = surfaceContainerHigh,
        onSurfaceSecondary = onSurfaceSecondary,
        surfaceContainer = surfaceContainer,
        onSurfaceContainer = onSurface,
        onSurfaceContainerHigh = onSurfaceContainerHigh,
        onSurfaceContainerHighest = onSurface,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        error = error,
        disabledPrimarySlider = ensureOpaqueOver(primary.copy(alpha = 0.38f), baseSurface),
        disabledPrimaryButton = ensureOpaqueOver(primary.copy(alpha = 0.38f), baseSurface),
        disabledOnPrimaryButton = ensureOpaqueOver(
            onPrimary.copy(alpha = 0.6f),
            ensureOpaqueOver(primary.copy(alpha = 0.38f), baseSurface),
        ),
        disabledSecondary = ensureOpaqueOver(outlineVariant.copy(alpha = 0.5f), baseSurface),
        disabledOnPrimary = ensureOpaqueOver(
            onPrimary.copy(alpha = 0.38f),
            ensureOpaqueOver(primary.copy(alpha = 0.38f), baseSurface),
        ),
        disabledOnSurface = ensureOpaqueOver(onSurface.copy(alpha = 0.38f), baseSurface),
        disabledOnSecondaryVariant = ensureOpaqueOver(onSurface.copy(alpha = 0.38f), disabledSecondaryVariant),
        sliderBackground = ensureOpaqueOver(primary.copy(alpha = 0.2f), baseSurface),
        sliderKeyPoint = primary,
        sliderKeyPointForeground = surfaceContainerHigh,
    )
}
