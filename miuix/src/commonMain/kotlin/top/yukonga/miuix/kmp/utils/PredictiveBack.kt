package top.yukonga.miuix.kmp.utils

import androidx.compose.runtime.compositionLocalOf

/**
 * 预测性返回手势的全局开关。
 *
 * 背景：App 有「设置 → 全局预测性返回动画」开关（默认开），关闭后返回键仍然被拦截、
 * 弹窗仍然关闭，只是不再驱动「滑到一半跟手」的动画。
 * 这个开关原先是 :app 里的一个裸 `var`，被 :miuix 的 4 个文件直接读 —— 那让这些文件
 * 无法进 commonMain。
 *
 * 改成 CompositionLocal 的原因（而不是再加一个 expect/actual）：
 * 它本来就该是「App 注入的行为开关」，和 MiuixTheme 的写法一致。
 * App 侧在自己的根上提供 [LocalPredictiveBackEnabled]，值来自它的偏好存储；
 * 没提供时用默认 [PredictiveBackEnabledDefault]（与迁移前的默认值一致：开）。
 */
val LocalPredictiveBackEnabled = compositionLocalOf { PredictiveBackEnabledDefault }

/** 未注入时的默认行为：开启预测性返回动画（与 App 迁移前的默认值相同）。 */
const val PredictiveBackEnabledDefault: Boolean = true