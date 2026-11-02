package com.haooz.chedule.i18n

/**
 * 系统首选语言（鸿蒙实现）。
 *
 * 目前返回 `"zh"`：真实值要走 ArkTS 的 `i18n.System.getSystemLanguage()`（需要一层 NAPI 桥），
 * 而 `systemLanguageTag()` 的契约是「空串 = 按非中文处理」—— 空串会让整个界面落到英文，
 * 对一个中文优先的应用是明显的错（实测启动就是全英文）。
 *
 * 所以先按「中文用户」的口径给 `"zh"`；用户仍可在设置里手动切语言（那个偏好优先于这里）。
 * 接桥后把这里换成同步查询即可，调用点不用动。
 */
actual fun systemLanguageTag(): String = "zh"
