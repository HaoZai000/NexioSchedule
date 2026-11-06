package com.haooz.chedule.ui.components

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.tanh
import top.yukonga.miuix.kmp.interactive.computeDragTransform
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.internal.drawBlurredPathShadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 这两颗按钮的拖动手感，与 [com.haooz.chedule.ui.basic.LiquidGlassTextButton] 共用。
 * 值统一定义在 miuix（玻璃档位），这里只做本模块别名。
 */
internal const val GlassDragStretchRatio = top.yukonga.miuix.kmp.interactive.GlassDragStretchRatio
internal const val GlassDragTranslationDamping = top.yukonga.miuix.kmp.interactive.GlassDragTranslationDamping

/**
 * 这两颗按钮的融球手感。
 *
 * 并体走的是 [LiquidFusion.weldRadiusDp] 那条场融合；[LiquidFusion.neckStrength] 只给底栏的
 * 贝塞尔颈用，对这对按钮不起作用（字段保留是为了和 [LiquidFusion.Default] 同一套参数）。
 */
val WebDavButtonFusion: LiquidFusion = LiquidFusion.Default.copy(
    neckStrength = 0.22f,
    bulgeMaxDp = 3f,
    bulgeRangeDp = 10f,
)

/**
 * WebDAV 页底部的「备份到云端 / 从云端恢复」。
 *
 * 两颗按钮是**一块玻璃**：轮廓由 [FusionPairShape] 描述，中间按 [WebDavButtonFusion] 缝合；
 * 两半同色，靠位置区分主次。按压 / 跟手位移 / 沿拖动方向拉伸都是逐半的，公式与
 * `LiquidGlassTextButton` 同一条 `computeDragTransform`（材质是 Path，所以形变只能改几何）。
 *
 * ⚠ 调用点必须在页面**录制层之外**（路由的 `overlay`、平板右栏的叠层 Box）：
 * 这块材质自己 `drawBackdrop` 采样同一层 backdrop，一旦放进页面内容里就会采样到自己 —— 自采自，直接崩。
 *
 * @param backdrop 宿主页面层的采样源；不能用这块材质所在的那层录制 backdrop。
 * @param fusionGap 两半的间距。小于 `2 × bulgeMaxDp`（这里是 6dp）山丘就会碰在一起，
 *   再近一点（约 `bulgeMaxDp` 量级）两团的距离场就被焊缝焊成一片。
 */
@Composable
fun WebDavActionButtons(
    backdrop: Backdrop,
    backingUp: Boolean,
    restoring: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    fusion: LiquidFusion = WebDavButtonFusion,
    fusionGap: Dp = 10.dp,
    /** 按钮高度。默认比 Miuix 的 `ButtonDefaults.MinHeight`（40dp）高一档 */
    height: Dp = 48.dp,
) {
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    val gapPx = with(density) { fusionGap.toPx() }
    val heightPx = with(density) { height.toPx() }
    val horizontalPadding = if (isTabletWidth()) 28.dp else 24.dp
    // 材质画布向外的余量：按压放大 + 跟手位移都会把形状推出可见矩形，
    // 不留这圈余量就会被节点边界切掉（拖动时边缘/高光断一刀）
    val bleedXPx = with(density) { 18.dp.toPx() }
    val bleedYPx = with(density) { 32.dp.toPx() }

    // 两半同色，都用主色（与 GlassTextButton 的 primary 同一档透明度）
    val surfaceColor = if (isLightTheme) {
        MiuixTheme.colorScheme.primary.copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.72f))
    } else {
        MiuixTheme.colorScheme.primary.copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.62f))
    }
    val textColor = Color.White

    val glassBlur = AppMaterialSettings.glassBlurDp()
    val chromeLens = AppMaterialSettings.chromeLensEnabled()
    val glassEffectsOn = AppMaterialSettings.glassEffectsEnabled()
    val surface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(surfaceColor) { { drawRect(surfaceColor) } }

    val leftHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope, lightPeakAlpha = 0.1f)
    }
    val rightHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope, lightPeakAlpha = 0.1f)
    }
    // 描边底色给白：`highlightTint` 会按底色染成同色系，蓝底上等于自己描自己 —— 整圈看不见。
    // 给白（饱和度 0）才走「白色高光」那条分支，两套主题下都亮。
    // 正常档走通用高光色；降级档按按钮底色同色系提亮（白描边在彩色按钮上很突兀）
    val edgeLight = rememberDefaultEdgeLight(degradedBaseColor = surfaceColor)

    // 下方投影：参数与 LiquidGlassTextButton 一致（下沉 4dp / 模糊 10 / primary 0.4）。
    // 这块材质浮在页面底栏上方，没有投影会像一张平贴的贴纸。
    val shadowColor = MiuixTheme.colorScheme.primary
    val shadowAlpha = 0.4f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .navigationBarsPadding()
            // 下发光：与 LiquidGlassTextButton 逐字一致（下沉 4dp / 模糊 10 / primary 0.4）。
            // ⚠ 必须挂在 bottom padding **之前**：模糊会向下溢出节点，挂在后面会被那 20dp 的
            //   内边距之外裁掉，只剩上沿一条亮边 —— 看起来就是「没有下发光」。
            // 高度用参数 height 而不是 size.height：节点含 20dp 内边距，圆角半径会算错。
            .drawBehind {
                val offsetY = 4.dp.toPx()
                val blurRadius = 10.dp.toPx()
                val buttonHeight = height.toPx()
                val radius = buttonHeight / 2f
                val halfWidth = (size.width - gapPx) / 2f
                // 与 ActionHalf 的 translationOf 同一条公式、同一个 minDim：
                // 两半等宽等高，所以同样的拖动量得到同样的位移 —— 阴影不会一半跟得紧一半松
                val minDim = minOf(halfWidth, heightPx).coerceAtLeast(1f)
                fun shiftOf(highlight: InteractiveHighlight): Offset {
                    val d = highlight.offset
                    return Offset(
                        minDim * tanh(GlassDragTranslationDamping * d.x / minDim),
                        minDim * tanh(GlassDragTranslationDamping * d.y / minDim),
                    )
                }
                // 两个胶囊各画一份、各跟各的位移：整块画一个圆角矩形会在中间 gap 处堆出一坨蓝影
                val shift = shiftOf(leftHighlight)
                val leftPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                shift.x,
                                offsetY + shift.y,
                                halfWidth + shift.x,
                                buttonHeight + offsetY + shift.y,
                            ),
                            cornerRadius = CornerRadius(radius, radius),
                        ),
                    )
                }
                drawBlurredPathShadow(
                    path = leftPath,
                    color = shadowColor.copy(alpha = shadowAlpha),
                    blurRadius = blurRadius.coerceAtLeast(0.1f),
                )
                val rightShift = shiftOf(rightHighlight)
                val rightPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                halfWidth + gapPx + rightShift.x,
                                offsetY + rightShift.y,
                                size.width + rightShift.x,
                                buttonHeight + offsetY + rightShift.y,
                            ),
                            cornerRadius = CornerRadius(radius, radius),
                        ),
                    )
                }
                drawBlurredPathShadow(
                    path = rightPath,
                    color = shadowColor.copy(alpha = shadowAlpha),
                    blurRadius = blurRadius.coerceAtLeast(0.1f),
                )
            }
            .padding(bottom = 20.dp)
    ) {
        val halfPx = with(density) { (maxWidth.toPx() - gapPx) / 2f }
        // 抓成本地值：进到内层 BoxScope 之后 maxWidth 会被隐式接收者挡住
        val visibleWidth = maxWidth
        // 材质节点尺寸（可见宽 + 两侧 bleed）。形状与折射共用，任何一边自己算都会错位
        val nodeSizePx = Size(
            with(density) { visibleWidth.toPx() } + bleedXPx * 2f,
            heightPx + bleedYPx * 2f,
        )

        fun scaleOf(hl: InteractiveHighlight): Offset {
            val d = computeDragTransform(
                width = halfPx,
                height = heightPx,
                fraction = 1f,
                pressProgress = hl.pressProgress,
                offset = hl.offset,
                density = density,
                // 整宽按钮必须传 1f，否则实测宽高比把纵向拉伸砍到近乎没有
                shapeAspectRatio = 1f,
                stretchRatio = GlassDragStretchRatio,
            )
            return Offset(d.scaleX, d.scaleY)
        }

        /** 跟手位移。用 [GlassDragTranslationDamping] 这条阻尼，与文字按钮同一数值 */
        fun translationOf(hl: InteractiveHighlight): Offset {
            val minDim = minOf(halfPx, heightPx).coerceAtLeast(1f)
            val d = hl.offset
            return Offset(
                minDim * tanh(GlassDragTranslationDamping * d.x / minDim),
                minDim * tanh(GlassDragTranslationDamping * d.y / minDim),
            )
        }

        // 材质轮廓的最近一帧：绘制期读它裁高光，形变时自然跟得上
        val outlineRef = remember { OutlineHolder() }
        // 每半手指的局部坐标（px）。高光画在材质层，需要知道按在哪儿
        val leftPointer = remember { FloatArray(2) }
        val rightPointer = remember { FloatArray(2) }

        val params = remember(gapPx, fusion, halfPx, leftHighlight, rightHighlight) {
            FusionPairParams(
                gapPx = gapPx,
                fusion = fusion,
                bleedX = bleedXPx,
                bleedY = bleedYPx,
                leftScale = { scaleOf(leftHighlight) },
                leftTranslation = { translationOf(leftHighlight) },
                rightScale = { scaleOf(rightHighlight) },
                rightTranslation = { translationOf(rightHighlight) },
                // 高光要按真实轮廓裁，所以把每次求出来的轮廓留一份给绘制期用
                outlineSink = { outlineRef.path = it },
            )
        }
        // drawBackdrop 的 ShapeProvider 按引用比较：每次现造实例，形变才会逐帧重新求轮廓
        val shapeFactory: () -> Shape = { FusionPairShape(params) }
        // edgeLight 按引用缓存 outline，所以要稳定实例 + 版本号（否则描边不跟形变）
        val edgeShape: Shape = remember(params) { FusionPairShape(params) }
        val revision: () -> Long = remember(leftHighlight, rightHighlight) {
            {
                var r = (leftHighlight.pressProgress * 2000f).fastRoundToInt().toLong()
                r = r * 31 + (leftHighlight.offset.x * 40f).fastRoundToInt()
                r = r * 31 + (leftHighlight.offset.y * 40f).fastRoundToInt()
                r = r * 31 + (rightHighlight.pressProgress * 2000f).fastRoundToInt()
                r = r * 31 + (rightHighlight.offset.x * 40f).fastRoundToInt()
                r = r * 31 + (rightHighlight.offset.y * 40f).fastRoundToInt()
                r
            }
        }

        // ── 折射 ──────────────────────────────────────────────────────────
        // `lens()` 对自定义 Shape 直接跳过（只认圆角矩形），所以自己喂一份沿融合轮廓走的 shader。
        // 几何与 FusionPairShape 共用 `params.frame(...)`：轮廓和折射读同一份快照，接缝才不会错开。
        val refractionShaderRef = remember { arrayOfNulls<RuntimeShader>(1) }
        val refractionScaleRef = remember { floatArrayOf(1f) }
        val refractionHeightPx = with(density) { 6.dp.toPx() }
        val refractionAmountPx = with(density) { 36.dp.toPx() }
        val feedRefraction: () -> Unit = {
            val sh = refractionShaderRef[0]
            if (sh != null) {
                val scale = refractionScaleRef[0]
                // 材质节点尺寸（含 bleed）。折射喂参数时拿不到 DrawScope，按已知量算即可
                val f = params.frame(nodeSizePx, density)
                if (f != null) {
                    sh.setFloatUniform("offset", 0f, 0f)
                    sh.setFloatUniform("lc", f.leftCx * scale, f.leftCy * scale)
                    sh.setFloatUniform("lh", f.leftHalfW * scale, f.leftHalfH * scale)
                    sh.setFloatUniform("lr", f.leftRx * scale, f.leftRy * scale)
                    sh.setFloatUniform("rc", f.rightCx * scale, f.rightCy * scale)
                    sh.setFloatUniform("rh", f.rightHalfW * scale, f.rightHalfH * scale)
                    sh.setFloatUniform("rr", f.rightRx * scale, f.rightRy * scale)
                    val n = f.neck
                    // 山丘参数无条件传：它在颈出现之前就有了，折射得跟着鼓
                    sh.setFloatUniform("bulgeH", (n?.bulgeH ?: 0f) * scale)
                    sh.setFloatUniform("bulgeAngleL", n?.bulgeAngle ?: 0f)
                    sh.setFloatUniform("bulgeAngleR", (n?.bulgeAngle ?: 0f) + 3.1415927f)
                    sh.setFloatUniform("bulgePhiL", n?.phiPill ?: 0f)
                    sh.setFloatUniform("bulgePhiR", n?.phiCircle ?: 0f)
                    sh.setFloatUniform("bulgeGaussEdge", n?.gaussEdge ?: 0f)
                    sh.setFloatUniform("bulgeSigma", fusion.bulgeSigma)
                    // 焊缝：与形状同一个场融合半径，轮廓和折射才不会错开
                    sh.setFloatUniform("weldK", f.weldK * scale)
                    sh.setFloatUniform("refractionHeight", refractionHeightPx * scale)
                    sh.setFloatUniform("refractionAmount", -refractionAmountPx * scale)
                }
            }
        }
        val pairEffects: BackdropEffectScope.() -> Unit = remember(
            chromeLens, glassBlur, glassEffectsOn, params, feedRefraction,
        ) {
            {
                if (glassEffectsOn) {
                    vibrancy()
                    // Mirror：默认 Clamp 会把边缘采样钳到最外一行，边缘一圈模糊退化
                    blur(glassBlur.dp.toPx(), TileMode.Mirror)
                    padding = 0f
                    if (chromeLens) {
                        refractionScaleRef[0] = downsampleScale
                        // AGSL 编译失败不能把异常抛进 draw 链路（会整应用崩）：拿不到就退化成「只有模糊」
                        runCatching {
                            refractionShaderRef[0] = obtainRuntimeShader(
                                "FusionPairRefraction",
                                FusionPairRefractionShader,
                            )
                            runtimeShaderEffect(
                                key = "FusionPairRefraction",
                                shaderString = FusionPairRefractionShader,
                                uniformShaderName = "content",
                            ) {
                                feedRefraction()
                            }
                        }
                    }
                }
            }
        }

        // 可见矩形：形状 / 内容都在这个坐标系里；材质节点靠 requiredSize 撑大、
        // 由父层居中，四周各多出一圈 bleed（不用 offset 挪，Alignment 就够了）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(
                        width = with(density) { (visibleWidth.toPx() + bleedXPx * 2f).toDp() },
                        height = with(density) { (heightPx + bleedYPx * 2f).toDp() },
                    )
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = shapeFactory,
                        effects = pairEffects,
                        highlight = null,
                        // 关掉库自带的黑阴影：下发光改用与 LiquidGlassTextButton 完全一致的那一层
                        // （drawBehind + drawBlurredPathShadow，下沉 4dp / 模糊 10 / primary 0.4）
                        shadow = null,
                        onDrawSurface = surface,
                        // 每帧刷折射几何：拖动 / 按压 / 拉伸时形状在变，updateEffects 不会重跑
                        onDrawBackdrop = { drawBackdrop ->
                            feedRefraction()
                            drawBackdrop()
                        },
                    )
                    .edgeLight(shape = edgeShape, edgeLight = edgeLight, outlineRevision = revision)
                    // 按压高光画在材质层、并按材质轮廓裁剪：挂在那半的矩形节点上会被裁成方块，
                    // 也不会跟着拉伸走。这里用最近一帧轮廓 clipPath，形变就自动跟随。
                    .drawWithContent {
                        drawContent()
                        val path = outlineRef.path ?: return@drawWithContent
                        val left = leftHighlight.pressProgress
                        val right = rightHighlight.pressProgress
                        if (left <= 0.01f && right <= 0.01f) return@drawWithContent
                        val peak = if (isLightTheme) 0.1f else 0.15f
                        val radius = heightPx * 1.5f
                        clipPath(path) {
                            if (left > 0.01f) {
                                // 手指是在「已位移」的半节点里量的，补回该半的 translation
                                val shift = translationOf(leftHighlight)
                                drawPressGlow(
                                    center = Offset(
                                        bleedXPx + leftPointer[0] + shift.x,
                                        bleedYPx + leftPointer[1] + shift.y,
                                    ),
                                    progress = left,
                                    peak = peak,
                                    radius = radius,
                                )
                            }
                            if (right > 0.01f) {
                                val shift = translationOf(rightHighlight)
                                drawPressGlow(
                                    center = Offset(
                                        bleedXPx + halfPx + gapPx + rightPointer[0] + shift.x,
                                        bleedYPx + rightPointer[1] + shift.y,
                                    ),
                                    progress = right,
                                    peak = peak,
                                    radius = radius,
                                )
                            }
                        }
                    }
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(fusionGap)
            ) {
                ActionHalf(
                    text = if (backingUp) ChromeStr.backingUp() else ChromeStr.backupToCloud(),
                    textColor = textColor,
                    highlight = leftHighlight,
                    translation = { translationOf(leftHighlight) },
                    pointer = leftPointer,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onBackup()
                    },
                    modifier = Modifier.weight(1f),
                )
                ActionHalf(
                    text = if (restoring) ChromeStr.restoring() else ChromeStr.restoreFromCloud(),
                    textColor = textColor,
                    highlight = rightHighlight,
                    translation = { translationOf(rightHighlight) },
                    pointer = rightPointer,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onRestore()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 承载最近一帧材质轮廓。普通字段即可：绘制期读，不需要触发重组。 */
private class OutlineHolder {
    var path: Path? = null
}

/** 按压高光：径向渐变 + Plus 叠加，调用方负责裁到材质轮廓内。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPressGlow(
    center: Offset,
    progress: Float,
    peak: Float,
    radius: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = peak * progress),
                Color.White.copy(alpha = peak * progress),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Plus,
    )
}

/** 可点的一半：文字跟着材质一起位移；手势与按压进度由 [highlight] 管。 */
@Composable
private fun ActionHalf(
    text: String,
    textColor: Color,
    highlight: InteractiveHighlight,
    translation: () -> Offset,
    pointer: FloatArray,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                val t = translation()
                translationX = t.x
                translationY = t.y
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            // 纯观察、不消费：只记录手指在这一半里的局部坐标，给材质层画高光
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pointer[0] = down.position.x
                    pointer[1] = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pointer[0] = change.position.x
                        pointer[1] = change.position.y
                        if (!change.pressed) break
                    }
                }
            }
            .then(highlight.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.button,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
