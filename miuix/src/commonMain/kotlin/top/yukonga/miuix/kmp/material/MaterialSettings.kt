package top.yukonga.miuix.kmp.material

import androidx.compose.runtime.compositionLocalOf

/**
 * 材质档位相关的两组能力，由 App 注入。
 *
 * 背景：本 fork 在很多 UI 上按「性能档」降级（关透镜、假渐进模糊、描边降级），
 * 但档位存在 App 的偏好存储里。模块不能反向依赖 :app，于是把这三项抽成
 * CompositionLocal —— App 在根部 provide，模块内任何位置都能读到，
 * 不必逐个调用点加参数（ProgressiveBlurTopBar 有 28 个调用点）。
 *
 * 与 [top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled] 同思路：
 * 「App 的行为偏好」用 CompositionLocal 注入，「平台能力」用 expect/actual。
 */

/**
 * 是否启用 chrome 透镜（折射）。
 *
 * 原读 App 的 AppMaterialSettings.chromeLensEnabled()：最佳档开，均衡及更省档关。
 * 默认关 —— 与不注入时的安全行为一致。
 */
val LocalChromeLensEnabled = compositionLocalOf { false }

/**
 * 渐进模糊是否用「假」实现（等值 blur + alpha 淡出，不跑 AGSL 多重采样）。
 *
 * 原读 AppMaterialSettings.progressiveBlurUseFake()：仅性能档为真。
 * 默认 false（跑完整管线）。
 */
val LocalUseFakeProgressiveBlur = compositionLocalOf { false }