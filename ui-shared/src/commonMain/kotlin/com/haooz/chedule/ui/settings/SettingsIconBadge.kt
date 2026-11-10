package com.haooz.chedule.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.softglass.shape.continuousClip
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.theme.LocalMonetColorEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 图标色块的取色器 —— **唯一**决定「多彩 / Monet 单色」的地方。
 *
 * 规则（产品确认）：
 * - 背景色固定取 [SettingsAccents] 的单色常量，**不跟随深浅模式**，也不受
 *   壁纸强制主题（LocalForcedDarkTheme）影响；
 * - 开启莫奈取色（且平台支持）时统一变 `primary` 底 + `onPrimary` 字形，忽略 accent。
 *   这是用户主动开启的取色开关，不属于深浅模式自动适配。
 *
 * ⚠ 做成不可变类而不是每行各写 if：色板切换必须全局一致，且避免每帧读主题造成多余重组。
 */
@Stable
class SettingsAccentResolver(
    private val monet: Boolean,
    private val primary: Color,
    private val onPrimary: Color,
) {
    /** 深浅模式切换不改变色块底色：非莫奈一律用色板里的固定常量。 */
    fun background(accent: Color): Color = if (monet) primary else accent

    fun foreground(accent: Color): Color = if (monet) onPrimary else Color.White
}

@Composable
fun rememberSettingsAccentResolver(): SettingsAccentResolver {
    // LocalMonetColorEnabled 在 CourseScheduleTheme 里已被提供成「偏好 && 平台支持」的有效值
    // ⚠ 这里刻意不读 isAppDarkTheme()：色块底色不随深浅模式（含壁纸强制主题）变化。
    val monet = LocalMonetColorEnabled.current
    val scheme = MiuixTheme.colorScheme
    return remember(monet, scheme.primary, scheme.onPrimary) {
        SettingsAccentResolver(
            monet = monet,
            primary = scheme.primary,
            onPrimary = scheme.onPrimary,
        )
    }
}

/**
 * 设置分类前置的「圆角色块 + 白色图标」。
 *
 * 尺寸：32dp 方块、8dp 连续圆角；图标**不指定尺寸**，走图标自带的 24dp 默认值
 * （所以四周各留 4dp 呼吸位）。`contentDescription = null`：图标纯装饰，标题已承载语义。
 *
 * 图标来源见 [SettingsIcon]：内置 [SettingsIcon.Vector] 与自定义 [SettingsIcon.Drawable]
 * 在这里统一解析成 [Painter]，两种都是单色矢量，一律由 [ColorFilter.tint] 上色。
 */
@Composable
fun SettingsIconBadge(
    icon: SettingsIcon,
    accent: Color,
    resolver: SettingsAccentResolver,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    corner: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .padding(end = 8.dp)
            .size(size)
            .continuousClip(corner)
            .background(resolver.background(accent)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = icon.rememberPainter(),
            contentDescription = null,
            colorFilter = ColorFilter.tint(resolver.foreground(accent)),
        )
    }
}

/** [SettingsIcon] → [Painter]。`painterResource` 是 `@Composable`，只能在这一层解析。 */
@Composable
private fun SettingsIcon.rememberPainter(): Painter = when (this) {
    is SettingsIcon.Vector -> rememberVectorPainter(vector)
    is SettingsIcon.Drawable -> painterResource(resource)
}

@Composable
fun SettingsCategoryBadge(category: SettingsCategory, resolver: SettingsAccentResolver) {
    SettingsIconBadge(
        icon = category.icon,
        accent = category.accent,
        resolver = resolver,
    )
}
