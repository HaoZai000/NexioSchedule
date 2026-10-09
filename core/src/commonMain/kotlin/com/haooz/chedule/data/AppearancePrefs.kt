package com.haooz.chedule.data

/**
 * 外观存储的偏好文件名与键名。
 *
 * ## 为什么单独抽出来
 *
 * 外观的实现（`ScheduleAppearance`，含 Bitmap 处理）还在 `:app`，但 `CourseRepository`
 * 需要判断「这个键是不是外观键」（`isCombinationBackupKey`，用来把外观挡在全量备份之外），
 * 于是 `:core` 反过来依赖了 `:app` 的类型 —— 那是反向依赖。
 *
 * 这里只把**两个字符串常量**下沉到 `:core`（它们本来就是数据兼容契约的一部分），
 * `:app` 的 `ScheduleAppearance` 用 `const val` 转发，两边取值同源、不会抄错。
 *
 * ⚠ 值**逐字未变**：改了等于老用户的外观设置读不出来。
 */
object AppearancePrefs {
    /** 外观独立 prefs 文件名（`ScheduleAppearance.FILE`） */
    const val FILE = "appearance_settings"

    /** 唯一键：`CombinationStyle` 的 JSON 快照（`ScheduleAppearance.FILE_STYLE_KEY`） */
    const val FILE_STYLE_KEY = "appearance_style"
}
