// 绘制期主题快照：供 DrawScope 等非组合上下文读主题状态——这些判断发生在 DrawScope 里（无法调 @Composable），
// 故缓存组合期结果为普通状态：isDark 主题是否深色（决定高光压多暗）；hasWallpaper 背后是否壁纸
// （浅色近白底加白会被截成白斑，高光需更弱）。置于 backdrop 而非各 App：纯 UI 语义不含业务知识，App 侧组合期写入。

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
