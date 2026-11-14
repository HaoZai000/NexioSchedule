package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.edgelight.EdgeLight
import com.kyant.backdrop.edgelight.rememberCardEdgeLight as backdropRememberCardEdgeLight
import com.kyant.backdrop.edgelight.rememberDefaultEdgeLight as backdropRememberDefaultEdgeLight

/**
 * 描边光在本项目里的接线点（用户 fork 版本，随 :backdrop 一起恢复）。
 *
 * backdrop 里的同名函数不知道 App 当前深浅色，这里把「是否浅色」注入；
 * 上游 1.7.0 的 AppMaterialSettings 已经没有 resolveEdgeLight 材质档，故先不接材质降级。
 */

@Composable
fun rememberDefaultEdgeLight(baseColor: Color? = null): EdgeLight =
    backdropRememberDefaultEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
    )

@Composable
fun rememberCardEdgeLight(baseColor: Color? = null): EdgeLight =
    backdropRememberCardEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
    )
