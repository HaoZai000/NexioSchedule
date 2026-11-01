package com.haooz.chedule.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.lightColorScheme

/**
 * Monet（壁纸取色）在鸿蒙端不可用，退到 Material3 的默认色板。
 *
 * 调用方（`CourseScheduleTheme` → `rememberAppThemeController`）本来就会先用
 * `isMonetColorAvailable()` 判断（那边看 `sdkLevel >= 31`，鸿蒙返回 0 = 不支持），
 * 所以这个函数正常路径不会被走到 —— 留一个中性实现是为了让 expect/actual 闭合。
 *
 * 接 Monet 时替换成从 ArkTS 的 `@ohos.app.ability.common` / 壁纸取色能力拿到的种子色，
 * 用 `material-color-utilities`（已经在 `:sharedMiuix` 的 vendor 里）生成 ColorScheme。
 */
@Composable
internal actual fun platformMonetMaterialColors(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else lightColorScheme()
