// Copyright (C) 2026 Haooz · AGPL-3.0-only

@file:OptIn(ExperimentalAtomicApi::class)

package top.yukonga.miuix.kmp.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.effects.blur
import top.yukonga.miuix.kmp.material.LocalMaterialNoneEnabled
import top.yukonga.miuix.kmp.material.LocalUseFakeProgressiveBlur
import com.kyant.backdrop.effects.runtimeShaderEffect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * shader 名唯一序号。
 *
 * 必须原子：并发组合若拿到同一个序号，两个 shader 会共用名字，
 * 而 shader 缓存按名字索引 —— 会导致取到错误的 shader。
 */
private val progressiveBlurShaderSeq = AtomicInt(0)
private val NoSampleTrack: () -> Float = { 0f }
private val FullMaskAlpha: () -> Float = { 1f }

/** 顶部最大模糊半径（dp）：糊带的半径从这里随 Y 收到 0 */
private val BlurMaxRadius = 12.dp

/**
 * 顶部没有状态栏时糊带额外收窄的量：80.dp 那档是按「有状态栏」的观感定的，
 * 顶部空出来后显得偏高，统一再收 8dp。课表顶栏自己算高度（传 `height`），共用这一个值。
 */
val NoStatusBarBlurReduction: Dp = 8.dp

/**
 * 读取任意状态并仅失效 draw，不进组合。
 * 侧栏伸缩时顶栏糊层需要跟手重采样，但顶栏自身尺寸不变。
 */
internal fun Modifier.invalidateDrawOnState(read: () -> Unit): Modifier =
    this then InvalidateDrawOnStateElement(read)

internal fun Modifier.invalidateDrawOnSampleTrack(track: () -> Float): Modifier =
    invalidateDrawOnState { track() }

private class InvalidateDrawOnStateElement(
    private val read: () -> Unit,
) : ModifierNodeElement<InvalidateDrawOnStateNode>() {

    override fun create(): InvalidateDrawOnStateNode = InvalidateDrawOnStateNode(read)

    override fun update(node: InvalidateDrawOnStateNode) {
        node.read = read
        node.reobserve()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InvalidateDrawOnStateElement) return false
        return read === other.read
    }

    override fun hashCode(): Int = read.hashCode()
}

private class InvalidateDrawOnStateNode(
    var read: () -> Unit,
) : DrawModifierNode, ObserverModifierNode, Modifier.Node() {

    override fun onObservedReadsChanged() {
        invalidateDraw()
        reobserve()
    }

    fun reobserve() {
        observeReads { read() }
    }

    override fun onAttach() {
        reobserve()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
    }
}

/**
 * 顶部栏渐进模糊：模糊半径随 Y 从顶部最大（[BlurMaxRadius]）连续收到 0。
 *
 * 四条渲染路径，按能力从高到低：
 * 1. **渐变遮罩**（无材质档）：无模糊，表面色 alpha 纵向渐变淡出。
 * 2. **AGSL 两趟**（`isRuntimeShaderSupported()`，API 33+ 且非性能档）：抖动采样 + 环形去噪，
 *    shader 与参数说明见 `ProgressiveBlurShaders.kt`。
 * 3. **假渐进**（性能档，或 AGSL 不可用但 RenderEffect 可用——即 API 31–32）：
 *    等值 `blur` + alpha 纵向淡出，观感接近、成本低一截。
 * 4. **渐变遮罩兜底**（无 RenderEffect）：统一限制后理论上不可达——API < 31 已被材质档锁进
 *    无材质（走 1），桌面 / iOS 恒支持。保留遮罩兜底，模糊不可用时不降级为纯色。
 */
@Composable
fun ProgressiveBlurTopBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    height: Dp = Dp.Unspecified,
    /**
     * 叠在模糊层上的表面色浓度（0 = 纯模糊）。只作用于糊层，不盖 [content]。
     * 仅 AGSL 真渐进吃这个值（shader 里按 Y 衰减）；假渐进不再叠表面色，两条路径观感对齐。
     */
    tintIntensity: Float = 0.2f,
    /** 表面色，默认取主题 surface */
    tintColor: Color = MiuixTheme.colorScheme.surface,
    blurAlpha: Float = 1f,
    /**
     * 性能档是否用「假渐进模糊」（等值 blur + alpha 淡出，不跑 AGSL 多重采样）。
     *
     * 由 [LocalUseFakeProgressiveBlur] 提供，避免让本文件反向依赖业务层的材质设置
     * （App 在根部 provide，28 个调用点无需逐个改）；也可显式传参覆盖。
     */
    useFakeProgressiveBlur: Boolean = LocalUseFakeProgressiveBlur.current,
    /**
     * 无材质档渐变遮罩：整条糊带换成无模糊的半透明渐变（不采样 backdrop）。
     * 默认从 [LocalMaterialNoneEnabled] 读，App 在根部 provide；也可显式传参覆盖。
     */
    useGradientMask: Boolean = LocalMaterialNoneEnabled.current,
    /**
     * 渐变遮罩的额外不透明度（0–1），在绘制期读，只失效绘制。
     *
     * 无材质档没有模糊层，遮罩就是顶栏唯一的「材质」；今日 / 课表页传顶栏材质 alpha，
     * 让它跟按钮背景同时出现（滚到顶时整条留空）。
     */
    maskAlpha: () -> Float = FullMaskAlpha,
    /** 底端透明淡出起点（0–1，相对糊层高度）。越小过渡越长。 */
    edgeFadeStart: Float = 0.88f,
    /** 侧栏伸缩等导致采样源位移后自增，强制重建糊层采样 */
    resampleKey: Int = 0,
    /**
     * 伸缩过程中的连续跟踪值；draw 阶段调用并只失效 draw，不进组合。
     * 传稳定 lambda（如 tabletNavExpandSampleTrack），避免参数每帧更新触发整树重组。
     */
    sampleTrack: () -> Float = NoSampleTrack,
    /** An overlaid native toolbar inset already consumed by the surrounding scaffold. */
    topInsetAdjustment: Dp = 0.dp,
    /**
     * true = 屏幕顶部没有状态栏（折叠屏 iPhone 折叠态，状态栏随竖排 tab bar 移到右侧）。
     * 形态由宿主按 `traitCollection.verticalBarEdge` 判定：算出来的高度是 0 分不出
     * 「顶部没有」和「被 `topInsetAdjustment` 吃光」，只有 trait 能区分。
     */
    noTopStatusBar: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val totalHeight = if (height != Dp.Unspecified) {
        height
    } else {
        val statusBarHeight = (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
            topInsetAdjustment).coerceAtLeast(0.dp)
        when {
            // 顶部压根没有状态栏（折叠屏折叠态）：别按「被导航栏吃完」那套撑到 120.dp
            noTopStatusBar -> 80.dp - NoStatusBarBlurReduction
            statusBarHeight > 0.dp -> 80.dp + statusBarHeight
            else -> 120.dp
        }
    }

    val blurShapeBlock: () -> androidx.compose.ui.graphics.Shape = remember { { RectangleShape } }
    // ShaderRegistry 按 key 共享 RuntimeShader；多顶栏同时挂载时必须各用独立 key，
    val shaderKey = remember(resampleKey) {
        "ProgressiveBlurRadial_${progressiveBlurShaderSeq.addAndFetch(1)}"
    }
    val denoiseKey = remember(shaderKey) { "${shaderKey}_denoise" }
    val blurEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(shaderKey, denoiseKey, tintColor, tintIntensity, edgeFadeStart) {
            {
                val maxRadiusPx = BlurMaxRadius.toPx()
                // padding = 0：shader 的采样全部镜像折回 content 区（见 ProgressiveBlurShaders）
                padding = 0f
                val contentW = size.width * downsampleScale
                val contentH = size.height * downsampleScale

                runtimeShaderEffect(shaderKey, PROGRESSIVE_BLUR_SHADER, "content") {
                    setFloatUniform("contentOrigin", 0f, 0f)
                    setFloatUniform("contentSize", contentW, contentH)
                    setFloatUniform("maxRadius", maxRadiusPx * downsampleScale)
                    setFloatUniform("edgeFadeStart", edgeFadeStart)
                    setColorUniform("tint", tintColor)
                    setFloatUniform("tintIntensity", tintIntensity)
                }
                runtimeShaderEffect(denoiseKey, PROGRESSIVE_DENOISE_SHADER, "content") {
                    setFloatUniform("contentOrigin", 0f, 0f)
                    setFloatUniform("contentSize", contentW, contentH)
                    setFloatUniform("maxRadius", maxRadiusPx * downsampleScale)
                }
            }
        }

    Box(modifier = modifier) {
        // 只重建糊层，不 remount content
        key(resampleKey, useFakeProgressiveBlur, useGradientMask) {
            when {
                // 无材质档：渐变遮罩，不采样 backdrop，无模糊
                useGradientMask -> {
                    ProgressiveBlurGradientMask(totalHeight, tintColor, blurAlpha, maskAlpha)
                }
                // 真渐进：AGSL 守 API 33+（31–32 上 runtimeShaderEffect 会静默 no-op，掉进这分支会整条糊带消失）
                isRuntimeShaderSupported() && !useFakeProgressiveBlur -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(totalHeight)
                            .graphicsLayer { alpha = blurAlpha }
                            .invalidateDrawOnSampleTrack(sampleTrack)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = blurShapeBlock,
                                effects = blurEffects,
                                highlight = null,
                                shadow = null
                            )
                    )
                }
                // 假渐进：性能档，或 AGSL 不可用但 RenderEffect 可用（API 31–32）
                isRenderEffectSupported() -> {
                    // 等值 blur + alpha 渐变淡出，观感接近真渐进，成本低一截
                    val fadeBrush = remember(totalHeight, edgeFadeStart) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White,
                                (edgeFadeStart * 0.55f).coerceIn(0.2f, 0.9f) to Color.White,
                                edgeFadeStart.coerceIn(0.4f, 0.95f) to Color.White.copy(alpha = 0.45f),
                                1.0f to Color.Transparent,
                            ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(totalHeight)
                            .graphicsLayer {
                                alpha = blurAlpha
                                compositingStrategy =
                                    androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                clip = true
                            }
                            .invalidateDrawOnSampleTrack(sampleTrack)
                            .drawWithContent {
                                drawContent()
                                // 只淡出糊层自身，不盖到 content()
                                drawRect(fadeBrush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                            }
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = blurShapeBlock,
                                effects = {
                                    // Mirror：`TileMode.Clamp`（blur 的默认）会把顶边的采样钳到第一行，
                                    // 12dp 半径下顶栏最上沿会被拉平成一条「没糊」的带子 —— 设置页最明显。
                                    // 镜像发生在节点边界内，和真模糊那套镜像采样同思路。
                                    blur(BlurMaxRadius.toPx(), TileMode.Mirror)
                                    // ⚠ Mirror 会让 blur() 把 padding 撑到 radius，而外扩的那圈缓冲
                                    // 录的是屏幕外（空），镜像过去仍是空 —— 压回 0，让镜像只发生在节点内。
                                    padding = 0f
                                },
                                highlight = null,
                                shadow = null,
                            )
                    )
                }
                // 兜底仍走渐变遮罩：模糊能力缺失时不降级为纯色（统一限制后理论不可达，见顶部文档）
                else -> {
                    ProgressiveBlurGradientMask(totalHeight, tintColor, blurAlpha, maskAlpha)
                }
            }
        }
        content()
    }
}

/**
 * 渐变遮罩：无模糊，表面色 alpha 纵向渐变淡出。
 * 无材质档的渐进模糊形态，也是能力缺失时的兜底（替代旧「纯色渐变」降级）。
 *
 * [maskAlpha] 在绘制期读：顶栏材质 alpha 逐帧变化时只失效这一层的绘制。
 */
@Composable
private fun ProgressiveBlurGradientMask(
    totalHeight: Dp,
    fillColor: Color,
    blurAlpha: Float,
    maskAlpha: () -> Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .graphicsLayer { alpha = blurAlpha * maskAlpha() }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to fillColor.copy(alpha = 0.98f),
                        0.4f to fillColor.copy(alpha = 0.92f),
                        0.7f to fillColor.copy(alpha = 0.6f),
                        1.0f to fillColor.copy(alpha = 0.0f)
                    ),
                )
            )
    )
}
