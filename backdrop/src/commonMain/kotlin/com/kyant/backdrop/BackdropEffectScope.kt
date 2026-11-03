package com.kyant.backdrop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.internal.MergedRefractionShaderString
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.internal.chain
import com.kyant.backdrop.internal.identityColorMatrix
import com.kyant.backdrop.internal.multiplyColorMatrix

sealed interface BackdropEffectScope : Density, RuntimeShaderCache {

    val size: Size

    /**
     * 离屏模糊缓冲的降采样比例；采样链路回退源库实现后缓冲为 1:1 全分辨率，故固定 1f（字段保留仅为
     * lens()/blur() 不改签名）。⚠ effect 中按像素缩放 uniform/半径的必须读此字段，切勿写死常量：
     * 曾残留 `DOWNSAMPLE_SCALE = 0.42f` 被 blur() 乘上，导致所有卡片模糊半径被缩到 42%。恢复降采样改此字段即可。
     */
    val downsampleScale: Float

    val layoutDirection: LayoutDirection

    val shape: Shape

    var padding: Float

    var renderEffect: RenderEffect?

    /** lens() 产生的 RuntimeShader，供 ImageShader 直采折射路径复用 */
    var lensShader: RuntimeShader?

    /** 全分辨率直采用的折射参数（屏幕像素） */
    var lensRefractionHeight: Float
    var lensRefractionAmount: Float
    var lensCornerRadii: FloatArray?
}

internal abstract class BackdropEffectScopeImpl : BackdropEffectScope, RuntimeShaderCache {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null
    override var lensShader: RuntimeShader? = null
    override var lensRefractionHeight: Float = 0f
    override var lensRefractionAmount: Float = 0f
    override var lensCornerRadii: FloatArray? = null
    override val downsampleScale: Float = 1f

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    // --- 效果链记录 ---
    // 效果 lambda 里每调一个算子就记一笔，跑完之后再看能不能把「颜色算子」折进折射着色器。
    // 只记录、不改变原有 eager 建链：不满足条件时整条旧链原样保留。
    private val opCodes = IntArray(OP_CAPACITY)
    private var opCount = 0
    private var rawOpCount = 0
    private var blurOpCount = 0
    private var blurOpRadius = 0f
    private var blurOpTileMode: TileMode = TileMode.Clamp
    private var lensOpCount = 0
    private var lensOpHeight = 0f
    private var lensOpAmount = 0f
    private var lensOpDepth = 0f
    private var lensOpDispersion = false
    private var tintOpCount = 0
    private val tintMatrix = FloatArray(20)

    /**
     * 上一次 [applyEffects] 是否真的把颜色段折进了折射着色器。
     * 仅供排查用（接真机 Perfetto 时确认某类玻璃件走的是哪条腿），不参与渲染。
     */
    internal var lastChainMerged: Boolean = false
        private set

    init {
        identityColorMatrix(tintMatrix)
    }

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaderCache.obtainRuntimeShader(key, string)
    }

    fun update(scope: DrawScope): Boolean {
        val newDensity = scope.density
        val newFontScale = scope.fontScale
        val newSize = scope.size
        val newLayoutDirection = scope.layoutDirection

        val changed = newDensity != density ||
                newFontScale != fontScale ||
                newSize != size ||
                newLayoutDirection != layoutDirection

        if (changed) {
            density = newDensity
            fontScale = newFontScale
            size = newSize
            layoutDirection = newLayoutDirection
        }

        return changed
    }

    /**
     * 求值一遍 effects lambda。
     *
     * 不叫 `apply`：那会遮蔽 Kotlin 标准库的 `apply`，任何直接持有 Impl 的调用点写
     * `scope.apply { ... }` 都会被解析成这个成员（lambda 接收者兼容），语义悄悄从「配置对象」变成「执行效果」。
     */
    fun applyEffects(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        lensShader = null
        lensRefractionHeight = 0f
        lensRefractionAmount = 0f
        lensCornerRadii = null
        resetChain()
        effects()
        compileChain()
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        lensShader = null
        lensRefractionHeight = 0f
        lensRefractionAmount = 0f
        lensCornerRadii = null
        resetChain()
        runtimeShaderCache.clear()
    }

    // --- 记录入口（供 effects/*.kt 调用）---

    internal fun recordBlurOp(radius: Float, tileMode: TileMode) {
        pushOp(OP_BLUR)
        blurOpCount++
        blurOpRadius = radius
        blurOpTileMode = tileMode
    }

    internal fun recordLensOp(
        refractionHeight: Float,
        refractionAmount: Float,
        depthEffect: Boolean,
        chromaticAberration: Boolean
    ) {
        pushOp(OP_LENS)
        lensOpCount++
        lensOpHeight = refractionHeight
        lensOpAmount = refractionAmount
        lensOpDepth = if (depthEffect) 1f else 0f
        lensOpDispersion = chromaticAberration
    }

    /** 线性颜色算子（饱和度 / 亮度 / 对比度 / 不透明度）：记下 4×5 矩阵，可与已有算子复合。 */
    internal fun recordTintOp(matrix: FloatArray) {
        pushOp(OP_TINT)
        tintOpCount++
        multiplyColorMatrix(tintMatrix, matrix)
    }

    /** 无法拆成矩阵的自定义效果（任意 ColorFilter / 自定义 RenderEffect / 自写 AGSL）。 */
    internal fun recordRawOp() {
        pushOp(OP_RAW)
        rawOpCount++
    }

    private fun pushOp(code: Int) {
        if (opCount < OP_CAPACITY) {
            opCodes[opCount] = code
        }
        opCount++
    }

    private fun resetChain() {
        opCount = 0
        rawOpCount = 0
        blurOpCount = 0
        blurOpRadius = 0f
        blurOpTileMode = TileMode.Clamp
        lensOpCount = 0
        lensOpHeight = 0f
        lensOpAmount = 0f
        lensOpDepth = 0f
        lensOpDispersion = false
        tintOpCount = 0
        identityColorMatrix(tintMatrix)
    }

    /**
     * 把「颜色 → 模糊 → 折射」压成「模糊 → 合并着色器」（无模糊时直接压成单 pass）。
     *
     * 颜色算子是逐像素的 4×5 矩阵，可以在折射着色器最后一次采样后顺手做掉，于是整段离屏 pass 消失：
     * 有模糊时 3 pass → 2 pass，没模糊时 2 pass → 1 pass。
     *
     * ⚠ 代价：颜色被挪到了模糊**之后**。方程上「线性算子可与卷积交换」成立，但**钳位不成立** ——
     * 饱和度 1.5 会把通道顶出 [0,1]，旧链是「先钳位再模糊」。基准实测（480×240，饱和度 1.5）：
     * 照片类壁纸只有 0.02% 的通道差 >2/255，纯色块 + 硬边则到 9.5%（高光溢色略有不同）。
     * 想要逐位一致就 [BackdropTuning.mergeColorIntoRefraction] 关掉，或把颜色算子写到 blur() 之后。
     *
     * 不合并的情形一律保持原有 RenderEffect 链，行为与改动前完全一致：
     * - 没有可折叠的颜色算子（只有模糊 + 折射，仍是 2 pass，省不出东西）
     * - 仅有折射（本来就是 1 pass）
     * - 顺序不是 `[颜色…] [模糊?] [颜色…] 折射`
     * - 出现任意自定义效果（`colorFilter(ColorFilter)` / `effect(RenderEffect)` / `runtimeShaderEffect`）
     * - 色散折射（7 次采样，未纳入合并着色器）
     * - 平台不支持 AGSL 图像滤镜（Android 12）
     */
    private fun compileChain() {
        lastChainMerged = false
        if (!BackdropTuning.mergeColorIntoRefraction) return
        if (rawOpCount > 0) return
        if (tintOpCount == 0) return
        if (lensOpCount != 1 || lensOpDispersion) return
        if (blurOpCount > 1) return
        if (!isMergeableOrder()) return
        if (!isRuntimeShaderEffectSupported()) return

        val cornerRadii = lensCornerRadii ?: return
        val scale = downsampleScale
        val shader = obtainRuntimeShader("MergedRefraction", MergedRefractionShaderString)
        shader.apply {
            setFloatUniform("size", size.width * scale, size.height * scale)
            setFloatUniform("offset", -padding * scale, -padding * scale)
            setFloatUniform(
                "cornerRadii",
                cornerRadii[0] * scale,
                cornerRadii[1] * scale,
                cornerRadii[2] * scale,
                cornerRadii[3] * scale
            )
            setFloatUniform("refractionHeight", lensOpHeight * scale)
            // 与 lens() 一致：位移方向取负
            setFloatUniform("refractionAmount", -lensOpAmount * scale)
            setFloatUniform("depthEffect", lensOpDepth)
            setFloatUniform("cmR", tintMatrix[0], tintMatrix[1], tintMatrix[2], tintMatrix[3])
            setFloatUniform("cmG", tintMatrix[5], tintMatrix[6], tintMatrix[7], tintMatrix[8])
            setFloatUniform("cmB", tintMatrix[10], tintMatrix[11], tintMatrix[12], tintMatrix[13])
            setFloatUniform("cmA", tintMatrix[15], tintMatrix[16], tintMatrix[17], tintMatrix[18])
            // 平移列在 Compose 的 ColorMatrix 里是 0..255，着色器按 0..1 工作
            setFloatUniform(
                "cmO",
                tintMatrix[4] / 255f,
                tintMatrix[9] / 255f,
                tintMatrix[14] / 255f,
                tintMatrix[19] / 255f
            )
        }
        val merged = RuntimeShaderEffect(shader, "content")
        renderEffect = if (blurOpCount == 1) {
            // 模糊仍是独立 pass：单 pass 内联的取样点密度撑不住大半径，边缘会出阶梯状重影。
            // 但颜色那一段已经被吃掉，3 pass → 2 pass。
            val radius = blurOpRadius * scale
            BlurEffect(radius, radius, blurOpTileMode).chain(merged)
        } else {
            // 无模糊：原本「颜色 → 折射」两段，现在一段
            merged
        }
        lastChainMerged = true
    }

    /**
     * 合法顺序：`[颜色…] [模糊?] [颜色…] 折射`。折射必须是最后一步（它只改采样坐标，
     * 颜色挪到它后面完全等价 —— 但挪到它前面之后再加别的算子就不一定了）。
     */
    private fun isMergeableOrder(): Boolean {
        if (opCount > OP_CAPACITY) return false
        var i = 0
        while (i < opCount && opCodes[i] == OP_TINT) i++
        if (i < opCount && opCodes[i] == OP_BLUR) i++
        while (i < opCount && opCodes[i] == OP_TINT) i++
        if (i >= opCount || opCodes[i] != OP_LENS) return false
        return i == opCount - 1
    }

    private companion object {
        const val OP_TINT = 1
        const val OP_BLUR = 2
        const val OP_LENS = 3
        const val OP_RAW = 4
        const val OP_CAPACITY = 8
    }
}
