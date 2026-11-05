package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.edgelight.EdgeLight
import com.kyant.backdrop.edgelight.rememberCardEdgeLight as backdropRememberCardEdgeLight
import com.kyant.backdrop.edgelight.rememberDefaultEdgeLight as backdropRememberDefaultEdgeLight

/**
 * 描边光在本项目里的接线点：backdrop 里的同名函数是纯 UI 的、不知道 App 当前深浅色与
 * 材质档策略，这里把「是否浅色」与「材质档降级」以参数注入补上。
 *
 * ## 两个色是分开的
 *
 * - [baseColor] 只决定**高光描边**的染色（通透玻璃 / 清透磨砂档）。默认 null = 通用白高光。
 *   传容器色会让描边和填充同色系，整圈糊在按钮边缘上，所以除非有特别理由否则别传。
 * - [degradedBaseColor] 只在**降级描边**（无材质档）时用：那时没有模糊高光，
 *   靠「同色系提亮一档」把按钮轮廓从背景里拉出来。这里传容器色是对的。
 *
 * 拆成两个参数是因为它们服务两个相反的目标：高光要「亮出来」，降级描边要「跟底色协调」。
 * 一个参数同时管两边，必然有一边将就。
 */
@Composable
fun rememberDefaultEdgeLight(
    baseColor: Color? = null,
    degradedBaseColor: Color? = baseColor,
): EdgeLight {
    val isLightTheme = !isAppDarkTheme()
    // 材质档必须在组合期读出来并进 remember key：只在计算块里读的话，档位切换能触发重组，
    // 但 key 没变会直接返回缓存的旧描边，降级要等下一次别的原因重组才生效。
    val materialLevel = AppMaterialSettings.level
    // 内层按 baseColor 生成高光；外层再按 degradedBaseColor 决定要不要换成降级纯色描边。
    // 两层分开 remember：degradedBaseColor 变了也得重建，而 backdrop 那层的 key 里没有它。
    val source = backdropRememberDefaultEdgeLight(
        baseColor = baseColor,
        isLightTheme = isLightTheme,
    )
    return remember(source, isLightTheme, degradedBaseColor, materialLevel) {
        AppMaterialSettings.resolveEdgeLight(source, isLightTheme, degradedBaseColor)
    }
}

@Composable
fun rememberCardEdgeLight(
    baseColor: Color? = null,
    degradedBaseColor: Color? = baseColor,
): EdgeLight {
    val isLightTheme = !isAppDarkTheme()
    // 同 [rememberDefaultEdgeLight]：档位是降级描边的输入，必须进 key。
    val materialLevel = AppMaterialSettings.level
    val source = backdropRememberCardEdgeLight(
        baseColor = baseColor,
        isLightTheme = isLightTheme,
    )
    return remember(source, isLightTheme, degradedBaseColor, materialLevel) {
        AppMaterialSettings.resolveEdgeLight(source, isLightTheme, degradedBaseColor)
    }
}
