// 绘制期主题快照 —— 供非组合上下文（draw 阶段）读取主题状态
//
// 背景：高光/描边的亮度要按「主题亮暗」和「背后是不是壁纸」分流，但这些判断发生在
// DrawScope 里 —— 那里调不了 @Composable。
// 这两个快照把组合期的结果缓存成普通状态供绘制期读：
//   - isDark      主题是否深色（决定高光压多暗）
//   - hasWallpaper 背后是不是壁纸（浅色下近白底加白会被截成白斑，故高光要更弱）
//
// 放在 backdrop 而不是各 App：它只描述「主题与背景」，是纯 UI 语义，不含业务知识。
// App 侧在自己的组合期写入即可（MainActivity / isAppDarkTheme）。

package com.kyant.backdrop

import androidx.compose.runtime.mutableStateOf

/** 主题亮暗的绘制期快照。true = 深色。 */
object AppThemeSnapshot {
    val isDark = mutableStateOf(false)
}

/** 当前可见主页面背后是否为壁纸的绘制期快照。 */
object PageBackdropSnapshot {
    val hasWallpaper = mutableStateOf(false)
}
