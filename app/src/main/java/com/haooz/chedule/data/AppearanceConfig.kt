package com.haooz.chedule.data

/**
 * 课表外观配置：收敛所有可调节的外观参数为一个不可变值对象。
 *
 * 取代 MainActivity 中原先分散的 10 个字段 × 4 份状态（cached/live/saved/original）= 40 个
 * mutableStateOf 的写法，避免手工同步遗漏导致的 bug（如 cardContentAlignment 曾在
 * enterCustomizePage 的快照捕获循环中漏掉 5 处 save/restore）。
 *
 * 新增外观字段只需在此处加一个属性，save/switch/restore 自动覆盖，无需在各调用点逐一补同步。
 */
data class AppearanceConfig(
    val cardBlurRadius: Float = 4f,
    val cardAlpha: Float = 0.15f,
    val cardHeight: Float = 54f,
    val cardCornerRadius: Float = 10f,
    val wallpaperBrightness: Float = 0f,
    val showBreakDividers: Boolean = true,
    val cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    val cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    val showClassroom: Boolean = true,
    val showTeacher: Boolean = true,
    val cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT
) {

    companion object {
        /** 从 Combination 提取外观配置 */
        fun fromCombination(c: Combination): AppearanceConfig = AppearanceConfig(
            cardBlurRadius = c.cardBlurRadius,
            cardAlpha = c.cardAlpha,
            cardHeight = c.cardHeight,
            cardCornerRadius = c.cardCornerRadius,
            wallpaperBrightness = c.wallpaperBrightness,
            showBreakDividers = c.showBreakDividers,
            cardContentAlignment = c.cardContentAlignment,
            cardTextColor = c.cardTextColor,
            showClassroom = c.showClassroom,
            showTeacher = c.showTeacher,
            cardRefraction = c.cardRefraction
        )
    }
}
