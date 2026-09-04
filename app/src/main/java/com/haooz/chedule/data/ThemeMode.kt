package com.haooz.chedule.data

enum class ThemeMode(val label: String, val prefsValue: String) {
    FOLLOW_WALLPAPER("跟随壁纸", "follow_wallpaper"),
    FOLLOW_APP("跟随应用", "system"),
    LIGHT("浅色模式", "light"),
    DARK("深色模式", "dark");

    companion object {
        /** 课表外观"默认主题"下拉专用的偏好 key，仅决定今日页/课程表页主题，与全局主题开关隔离 */
        const val SCHEDULE_THEME_MODE_KEY = "schedule_theme_mode"

        fun fromPrefsValue(value: String?): ThemeMode =
            entries.find { it.prefsValue == value } ?: FOLLOW_WALLPAPER
    }
}
