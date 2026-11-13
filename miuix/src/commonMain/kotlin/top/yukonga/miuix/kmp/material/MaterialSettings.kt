package top.yukonga.miuix.kmp.material

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.haooz.softglass.platform.GlassRuntimeShader
import top.yukonga.miuix.kmp.theme.LocalAppSettingDark
import top.yukonga.miuix.kmp.theme.LocalForcedDarkTheme

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

/** 无材质档：玻璃材质整体关闭，渐进模糊降级为渐变遮罩。 */
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
val LocalGlassDialogBlurDp = compositionLocalOf { 12f }

/** 玻璃底色 alpha 降幅（柔光玻璃档专用，0 = 不降），浅色 / 深色各一档。 */
@Immutable
data class GlassSurfaceAlphaDrop(
    val light: Float = 0f,
    val dark: Float = 0f,
)

val LocalGlassSurfaceAlphaDrop = compositionLocalOf { GlassSurfaceAlphaDrop() }

/**
 * 柔光玻璃注入：shader 源码 + uniform 灌装（尺寸 / 圆角逐帧传入）。
 * App 在根部 provide；null = 不启用，玻璃件只留实色底（不再回落旧效果）。
 */
@Immutable
class SoftGlassLayer(
    val shaderString: String,
    /** radiusPx = null 表示胶囊（取短边一半）。 */
    val applyUniforms: GlassRuntimeShader.(width: Float, height: Float, radiusPx: Float?) -> Unit,
    /**
     * 悬浮大控件（展开的下拉面板）的 uniform 灌装，参数同 [applyUniforms]。
     * 默认回落到小控件材质；App 根部下发两套 style，这样只给大玻璃件换材质，
     * 搜索框仍是小控件材质。对话框面板另走 [applyDialogUniforms]。
     */
    val applyLargeUniforms: GlassRuntimeShader.(width: Float, height: Float, radiusPx: Float?) -> Unit =
        applyUniforms,
    /**
     * 对话框面板（[top.yukonga.miuix.kmp.overlay.OverlayDialog] /
     * [top.yukonga.miuix.kmp.window.WindowDialog] 共用的 DialogContentLayout）的 uniform 灌装，
     * 参数同 [applyUniforms]。App 根部下发 `BarFill + BrightLargeTone`：
     * 面板与底栏/下拉同属大控件，但底色单独按「面板混合」调，不能让 ListPopup 跟着换。
     * 默认回落到 [applyLargeUniforms]。
     */
    val applyDialogUniforms: GlassRuntimeShader.(width: Float, height: Float, radiusPx: Float?) -> Unit =
        applyLargeUniforms,
)

/** 浅 / 深各一份、样式已在组合期绑定好的柔光玻璃层 */
@Immutable
data class SoftGlassLayerVariants(
    val light: SoftGlassLayer? = null,
    val dark: SoftGlassLayer? = null,
) {
    fun forDark(isDark: Boolean): SoftGlassLayer? = if (isDark) dark else light
}

val LocalSoftGlassLayerVariants = compositionLocalOf { SoftGlassLayerVariants() }

/**
 * 弹窗快照带过来的生效深浅；null = 按当前作用域的 CompositionLocal 自行推导。
 * 弹窗 / 下拉渲染在 root host，读不到声明页面的 forced 口径，靠 `MiuixOverlayTheme` 传值。
 */
val LocalGlassIsDark = compositionLocalOf<Boolean?> { null }

/**
 * 当前作用域生效的深浅色：页面强制口径 > 应用主题口径 > 系统口径。
 */
@Composable
@ReadOnlyComposable
fun currentGlassIsDark(): Boolean =
    LocalGlassIsDark.current
        ?: LocalForcedDarkTheme.current
        ?: LocalAppSettingDark.current
        ?: isSystemInDarkTheme()

/** 当前作用域生效的柔光玻璃层；绘制期不再读任何全局状态。 */
@Composable
@ReadOnlyComposable
fun currentSoftGlassLayer(): SoftGlassLayer? =
    LocalSoftGlassLayerVariants.current.forDark(currentGlassIsDark())

