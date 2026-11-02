package com.kyant.backdrop.edgelight

import androidx.compose.runtime.Composable
import com.kyant.backdrop.AppThemeSnapshot

/**
 * EdgeLight 的「系统是否浅色」。
 *
 * 读 backdrop 自己的绘制期快照（由 App 在组合期写入），**不反向依赖上层模块** ——
 * 库不能知道 App 的主题实现。
 */
@Composable
actual fun isSystemLightTheme(): Boolean = !AppThemeSnapshot.isDark.value
