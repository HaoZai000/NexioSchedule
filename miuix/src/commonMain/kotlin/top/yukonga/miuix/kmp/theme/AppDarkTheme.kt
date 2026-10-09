package top.yukonga.miuix.kmp.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 强制深色主题的覆盖开关。null = 不覆盖（跟随外层主题）。
 *
 * 原先定义在 :app 的 ThemeUtils.kt 里，是纯 CompositionLocal、无平台依赖，
 * 所以直接搬进本模块：BlurBottomSheet 会在内部提供 `null` 来阻止外层 App 的
 * 强制深色渗进弹窗（弹窗自己按 sheetAppDark 决定）。
 *
 * 放在 theme 包是因为 MiuixTheme 的 forceDark 也会读它 —— 属于主题语义而非 App 偏好。
 */
val LocalForcedDarkTheme = staticCompositionLocalOf<Boolean?> { null }

/**
 * App 偏好里记录的「是否深色」（跟随系统 / 强制浅 / 强制深）。
 *
 * 这里只声明契约：真实数据在 App 的 SharedPreferences 里，Android 侧的实现见
 * ThemeUtils.android.kt；Skia 侧退回系统深浅色（iOS 接入时换成 NSUserDefaults 即可）。
 * App 若要精确对齐自己的偏好，在 Android 实现里替换读取来源即可，调用方无感。
 */
@Composable
expect fun rememberAppSettingDark(): Boolean