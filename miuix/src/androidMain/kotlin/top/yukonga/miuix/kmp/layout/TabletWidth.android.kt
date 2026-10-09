package top.yukonga.miuix.kmp.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Android 侧：保持迁移前的原判断 `screenWidthDp >= 600`。
 *
 * 用 `LocalConfiguration` 而非别的方案，是为了**零行为变化** ——
 * 分屏、折叠屏、多窗口下 Android 的 dp 宽度口径与原代码完全一致。
 */
@Composable
actual fun isTabletWidth(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 600