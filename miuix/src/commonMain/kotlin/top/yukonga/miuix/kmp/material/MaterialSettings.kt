package top.yukonga.miuix.kmp.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.edgelight.EdgeLight
import com.kyant.backdrop.edgelight.isSystemLightTheme

/**
 * 材质档位能力的注入点。
 *
 * 档位存在 App 的偏好里，而模块不能反向依赖 :app，所以由 App 在根部 provide，
 * 模块内随处可读（ProgressiveBlurTopBar 有 28 个调用点，不适合逐个加参数）。
 * 与 [top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled] 同思路：
 * 「App 的行为偏好」用 CompositionLocal，「平台能力」用 expect/actual。
 *
 * ⚠ 弹窗 / 对话框 / 下拉菜单渲染在 root host，读不到声明页面的 CompositionLocal，
 * 这些值必须随 `MiuixOverlayHostState` 的主题快照一起带过去（见 `MiuixOverlayTheme`）。
 * 各值默认取「无折射 / 不降级」的一方，与不注入时行为一致。
 */

/** chrome 透镜（折射）开关：通透玻璃档开，其余档关。 */
val LocalChromeLensEnabled = compositionLocalOf { false }

/** 无材质档：玻璃材质（模糊 / 折射 / vibrancy）整体关闭，渐进模糊降级为渐变遮罩。 */
val LocalMaterialNoneEnabled = compositionLocalOf { false }

/**
 * 渐进模糊是否用「假」实现（等值 blur + alpha 淡出，不跑 AGSL 多重采样）：
 * App 的「真·渐进模糊」开关关闭时为真；无材质档走渐变遮罩，不受它影响。
 */
val LocalUseFakeProgressiveBlur = compositionLocalOf { false }

/** 玻璃件（搜索框 / 输入框等）的采样模糊半径（dp）。 */
val LocalGlassBlurDp = compositionLocalOf { 8f }

/** 弹出面板（下拉菜单）的采样模糊半径（dp）。 */
val LocalGlassPanelBlurDp = compositionLocalOf { 24f }

/** 对话框 / 贴底弹窗的采样模糊半径（dp）。 */
val LocalGlassDialogBlurDp = compositionLocalOf { 24f }

/** 玻璃底色 alpha 降幅（通透玻璃档专用，0 = 不降），浅色 / 深色各一档。 */
@Immutable
data class GlassSurfaceAlphaDrop(
    val light: Float = 0f,
    val dark: Float = 0f,
)

val LocalGlassSurfaceAlphaDrop = compositionLocalOf { GlassSurfaceAlphaDrop() }

/**
 * 描边光降级解析器：材质档策略在 App 侧，:miuix 不能反向依赖，只能注入。
 *
 * 入参是 backdrop 生成的高光描边、当前是否浅色、以及调用点传入的底色（降级时按它提亮）。
 * 默认原样返回，与不注入时行为一致。App 侧必须让实例身份随材质档变化
 * （`remember(level) { ... }`），否则调用点按它做 key 时切档不会重算。
 */
val LocalEdgeLightResolver =
    compositionLocalOf<(EdgeLight, Boolean, Color?) -> EdgeLight> { { base, _, _ -> base } }

/**
 * 模块内统一取描边光。
 *
 * backdrop 的 `rememberDefaultEdgeLight` 只按亮暗 / 底色缓存，不知道 App 材质档，
 * 切到无材质档后仍会返回旧的高光描边。这里把注入的解析器放进 `key`：
 * 解析器身份随档位变，切档时整组重算，降级当场生效。
 */
@Composable
fun rememberMaterialEdgeLight(
    baseColor: Color? = null,
    isLightTheme: Boolean = isSystemLightTheme(),
): EdgeLight {
    val resolver = LocalEdgeLightResolver.current
    return key(resolver) {
        com.kyant.backdrop.edgelight.rememberDefaultEdgeLight(
            baseColor = baseColor,
            isLightTheme = isLightTheme,
            resolveMaterial = resolver,
        )
    }
}
