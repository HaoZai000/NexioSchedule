package com.haooz.chedule.ui.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick as onSemanticsClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.components.GlassDragStretchRatio
import com.haooz.chedule.ui.components.GlassDragTranslationDamping
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.chedule.ui.glass.ScheduleSoftGlassStyle
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.interactive.DragSelectAxis
import top.yukonga.miuix.kmp.interactive.DropdownMaskMode
import top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState
import top.yukonga.miuix.kmp.interactive.computeDragTransform
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.dropdownPanelEntry
import top.yukonga.miuix.kmp.interactive.dropdownPanelSlidingMask
import top.yukonga.miuix.kmp.internal.drawBlurredPathShadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val MenuEnterEasing = CubicBezierEasing(0.3f, 1.25f, 0.32f, 1f)
private val MenuExitEasing = CubicBezierEasing(0.3f, 1f, 0.3f, 1f)
/** 快捷条的柔光 shader 缓存键：尺寸与其他玻璃件不同，不能共用 uniform。 */
private const val LIVE_SHORTCUT_GLASS_KEY = "NexioSoftGlassLiveShortcutMenu"
/**
 * 菜单布局四周的阴影留白：面板可见区比测量尺寸四边各小这么多。
 * 调用方算「与卡片的间距 / 右缘对齐」要按它换算（见 OverlayLayers 的 offset）。
 */
internal val ShortcutMenuShadowPadding = 12.dp

data class ShortcutMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val iconSize: Dp = 26.dp
)

@Composable
fun ShortcutMenu(
    show: Boolean,
    items: List<ShortcutMenuItem>,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop,
    anchorRightPx: Float = Float.MAX_VALUE,
    onDismiss: () -> Unit = {},
    onMeasuredSize: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    val isLightTheme = !isAppDarkTheme()
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val containerWidthPx = windowInfo.containerSize.width

    val containerColor =
        if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainer
            .copy(alpha = com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(if (isLightTheme) 0.6f else 0.54f))
        else if (isLightTheme) Color(0xFFFFFFFF)
            .copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.6f))
        else Color(0xFF121212).copy(com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.54f))

    // 柔光玻璃：材质、档位、调色全由材质层给出，这里只声明用 chrome（悬浮面板）那一套。
    // 无材质档不跑 shader，必须留容器色，否则面板变成透空圆孔。
    val glassBlur = com.haooz.chedule.ui.utils.AppMaterialSettings.glassBlurDp()
    val glassEffectsOn = com.haooz.chedule.ui.utils.AppMaterialSettings.glassEffectsEnabled()
    val shortcutGlassStyle = com.haooz.chedule.ui.utils.AppMaterialSettings.chromeGlassStyle(
        base = ScheduleSoftGlassStyle,
        isDark = !isLightTheme,
    )
    val cornerRadiusPx = with(density) { 18.dp.toPx() }
    // drawBackdrop 按引用比较 shape / effects / onDrawSurface；lambda 每帧新建会让节点
    // 每帧 invalidateDraw、重新录制采样层并重跑采样。
    val shortcutShapeBlock: () -> Shape = remember { { ContinuousRoundedRectangle(20.dp) } }
    val glassEffects: com.haooz.softglass.backdrop.GlassEffectScope.() -> Unit =
        remember(glassBlur, glassEffectsOn, shortcutGlassStyle, cornerRadiusPx) {
            {
                if (glassEffectsOn) {
                    blur(glassBlur.dp.toPx())
                    runtimeShaderEffect(
                        key = LIVE_SHORTCUT_GLASS_KEY,
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applySoftGlass(
                            style = shortcutGlassStyle,
                            width = size.width,
                            height = size.height,
                            radiusPx = cornerRadiusPx,
                        )
                    }
                }
            }
        }
    val surfaceOnDraw: DrawScope.() -> Unit = remember(containerColor) {
        { drawRect(containerColor) }
    }

    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    val hapticFeedback = LocalHapticFeedback.current
    // 跟手滑选：横排按 X 轴命中，与下拉菜单共用同一套状态 / 遮罩 / 手势
    // （DropdownPanelDragSelect：按下命中 → 逐帧改命中 → 松手执行）
    val dragSelectState = remember {
        DropdownPanelDragSelectState(
            axis = DragSelectAxis.Horizontal,
            maskMode = DropdownMaskMode.Sliding,
        )
    }
    // 点按高光与形变共用一个实例：gestureModifier 驱动，modifier 画光，
    // pressProgress / offset 喂给 computeDragTransform 做按压缩放与跟手位移
    val dragScope = rememberCoroutineScope()
    val interactiveHighlight = remember(dragScope) {
        InteractiveHighlight(
            animationScope = dragScope,
            radiusScale = { 0.5f / 1.5f },
            radiusBaseDp = 150.dp,
            fixedRadius = true,
        )
    }

    // 默认向右展开；可见右边缘超出屏幕安全边距时向左平移，右边缘对齐卡片右边缘
    // 菜单 layout 宽含左右 ShortcutMenuShadowPadding，可见右边缘 = menuPositionX + menuWidth - ShortcutMenuShadowPadding
    var menuPositionX by remember { mutableStateOf(0f) }
    var menuWidth by remember { mutableStateOf(0) }
    val safetyPaddingPx = with(density) { 4.dp.toPx() }
    val shadowPadPx = with(density) { ShortcutMenuShadowPadding.toPx() }
    val screenRightEdge = containerWidthPx - safetyPaddingPx
    val shouldShiftLeft = remember(menuPositionX, menuWidth, screenRightEdge) {
        val visibleRightEdge = menuPositionX + menuWidth - shadowPadPx
        visibleRightEdge > screenRightEdge
    }
    // 左移目标：卡片右边缘（不超出屏幕安全边距）
    val shiftTarget = minOf(anchorRightPx, screenRightEdge)
    val shiftLeftPx = if (shouldShiftLeft) {
        (menuPositionX + menuWidth - shadowPadPx - shiftTarget).coerceAtLeast(0f)
    } else 0f

    LaunchedEffect(show) {
        if (show) {
            launch {
                scale.animateTo(1f, tween(420, easing = MenuEnterEasing))
            }
            launch {
                alpha.animateTo(1f, tween(240))
            }
        } else {
            launch {
                scale.animateTo(0.24f, tween(240, easing = MenuExitEasing))
            }
            launch {
                alpha.animateTo(0f, tween(120))
            }
        }
    }

    if (alpha.value <= 0f && !show) return

    val itemWidth = 58.dp
    val itemHeight = 45.dp
    val contentPadding = 6.dp
    val itemShape = remember { ContinuousRoundedRectangle(14.dp) }

    Box(
        modifier = modifier
            .wrapContentSize()
            .onGloballyPositioned { coordinates ->
                onMeasuredSize(coordinates.size.width, coordinates.size.height)
                menuWidth = coordinates.size.width
                menuPositionX = coordinates.positionInWindow().x
            }
            .graphicsLayer {
                val drag = computeDragTransform(
                    width = size.width,
                    height = size.height,
                    fraction = 1f,
                    pressProgress = interactiveHighlight.pressProgress,
                    offset = interactiveHighlight.offset,
                    density = this,
                    shapeAspectRatio = 1f,
                    stretchRatio = GlassDragStretchRatio,
                    translationDamping = GlassDragTranslationDamping,
                    pressScaleDp = 2f,
                )
                // 拉伸交给 drawBackdrop 的 layerBlock（源库采样时自动逆缩放），这里只留进场缩放
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                // 需要左移时 pivot 改为右下角(向左展开)，整体向左平移对齐目标右边缘
                transformOrigin = TransformOrigin(
                    if (shouldShiftLeft) 1f else 0f,
                    1f
                )
                translationX = (if (shouldShiftLeft) -shiftLeftPx else 0f) + drag.translationX
                translationY = drag.translationY
                clip = false
            }
            .drawBehind {
                val blurRadius = 16f * density.density
                val cornerRadiusPx = 20f * density.density
                // 跨平台阴影：compose Path + miuix expect/actual（原为 Paint+BlurMaskFilter+"#0A000000"）
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                ShortcutMenuShadowPadding.toPx(),
                                ShortcutMenuShadowPadding.toPx(),
                                size.width - ShortcutMenuShadowPadding.toPx(),
                                size.height - ShortcutMenuShadowPadding.toPx(),
                            ),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        ),
                    )
                }
                drawBlurredPathShadow(
                    path = path,
                    color = Color(0x0A000000),
                    blurRadius = blurRadius,
                )
            }
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(ShortcutMenuShadowPadding)
                // 面板内容区在 root 里的位置：项把 boundsInRoot 换算成面板局部坐标。
                // 与手势同一段链条，两个坐标系同源，ShortcutMenuShadowPadding 不掺进差值
                .onGloballyPositioned {
                    dragSelectState.panelTopInRoot = it.boundsInRoot().top
                    dragSelectState.panelLeftInRoot = it.boundsInRoot().left
                }
                .dropdownPanelDragSelect(
                    state = dragSelectState,
                    fraction = { 1f },
                    hapticFeedback = hapticFeedback,
                )
                // 按压/拖动位移源：纯视觉观察者，不消费事件（与下拉菜单同一挂法）；
                // 光跟手、位移喂给上面的 computeDragTransform 做形变
                .then(interactiveHighlight.gestureModifier)
                .drawBackdrop(
                    backdrop = backdrop,
                    shadow = null,
                    shape = shortcutShapeBlock,
                    effects = glassEffects,
                    layerBlock = {
                        val drag = computeDragTransform(
                            width = size.width,
                            height = size.height,
                            fraction = 1f,
                            pressProgress = interactiveHighlight.pressProgress,
                            offset = interactiveHighlight.offset,
                            density = this,
                            shapeAspectRatio = 1f,
                            stretchRatio = GlassDragStretchRatio,
                            translationDamping = GlassDragTranslationDamping,
                            pressScaleDp = 2f,
                        )
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                    },
                    // 柔光自己画玻璃色与方向光，再叠容器色会把折射盖住
                    onDrawSurface = if (glassEffectsOn) null else surfaceOnDraw,
                )
                .clip(ContinuousRoundedRectangle(18.dp))
                .then(interactiveHighlight.modifier)
        ) {
            Row(
                modifier = Modifier
                    .padding(contentPadding)
                    .dropdownPanelSlidingMask(dragSelectState, shape = itemShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    // 松手时由面板手势层统一触发（与下拉菜单一致）
                    val activate = {
                        onDismiss()
                        item.onClick()
                    }
                    Box(
                        modifier = Modifier
                            .size(width = itemWidth, height = itemHeight)
                            .clip(itemShape)
                            // 登记横向区间 + 命中高亮（遮罩色与 150ms 淡变都取菜单同一套）
                            .dropdownPanelEntry(
                                enabled = true,
                                state = dragSelectState,
                                highlightShape = itemShape,
                                action = activate,
                            )
                            // 面板手势层没有语义动作，读屏/键盘仍要能触发
                            .semantics {
                                role = Role.Button
                                onSemanticsClick {
                                    activate()
                                    true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(item.iconSize)
                        )
                    }
                }
            }
        }
    }
}
