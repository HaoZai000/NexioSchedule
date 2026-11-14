package com.kyant.backdrop.edgelight

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/** Skia（iOS / Desktop / wasm）实现 —— 读系统深浅色。 */
@Composable
actual fun isSystemLightTheme(): Boolean = !isSystemInDarkTheme()
