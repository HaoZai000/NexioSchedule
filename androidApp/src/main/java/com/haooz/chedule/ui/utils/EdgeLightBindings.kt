package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.edgelight.EdgeLight
import com.kyant.backdrop.edgelight.rememberCardEdgeLight as backdropRememberCardEdgeLight
import com.kyant.backdrop.edgelight.rememberCourseCardEdgeLight as backdropRememberCourseCardEdgeLight
import com.kyant.backdrop.edgelight.rememberDefaultEdgeLight as backdropRememberDefaultEdgeLight

/**
 * 描边光在本项目里的接线点。
 *
 * backdrop 里的同名函数是纯 UI 的（commonMain，不能反向依赖业务），它不知道
 * 「本 App 当前是浅色还是深色」「性能档是否要把高光降级成纯色描边」——
 * 这两件事都属于 App 策略，所以在这里补上。
 *
 * 这样做的收益：:app 侧 14 处 `rememberDefaultEdgeLight(baseColor = ...)` 调用点
 * 一行都不用改，迁移前后观感也完全一致；而 backdrop 里那份保持了跨平台纯净。
 *
 * 迁移前 edgelight 在 :app 内，函数体直接读 isAppDarkTheme() 与 AppMaterialSettings；
 * 现在这两件事以参数形式注入 backdrop，行为等价。
 */

@Composable
fun rememberDefaultEdgeLight(baseColor: Color? = null): EdgeLight =
    backdropRememberDefaultEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
        resolveMaterial = AppMaterialSettings::resolveEdgeLight,
    )

@Composable
fun rememberCourseCardEdgeLight(baseColor: Color? = null): EdgeLight =
    backdropRememberCourseCardEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
        resolveMaterial = AppMaterialSettings::resolveEdgeLight,
    )

@Composable
fun rememberCardEdgeLight(baseColor: Color? = null): EdgeLight =
    backdropRememberCardEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
        resolveMaterial = AppMaterialSettings::resolveEdgeLight,
    )
