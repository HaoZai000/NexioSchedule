package com.haooz.chedule.ui.basic

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

private val ShadowPadding = 24.dp

/** 收起态圆形按钮直径，也是容器变换的起点尺寸 */
private val ButtonDiameter = 42.dp

/** 面板宽度与圆角 */
private val PanelWidth = 200.dp
private val PanelCornerRadius = 25.dp

/**
 * 课程表右上角「更多」按钮 + 下拉菜单，**同一个控件**：容器变换（container transform）。
 *
 * 收起（fraction=0）：一个 42dp 的玻璃盒，圆角 25dp 被钳到 21dp = 正圆，即那颗圆形按钮，
 * 内部是 [triggerIcon]。
 * 展开（fraction=1）：同一个玻璃盒从 42dp 连续长到 [PanelWidth] × 面板高度，左上角固定，
 * 圆角始终 25dp → 自然变成圆角矩形面板；图标与菜单项都在这个盒子里，随盒子一起被裁剪。
 *
 * 没有第二个按钮、没有交叉淡入淡出：尺寸、圆角、玻璃、内容都在同一棵子树里连续变化。
 */
@Composable
fun LiquidGlassDropdownMenu(
    show: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    fraction: Animatable<Float, *> = remember { Animatable(0f) },
    onDismiss: (() -> Unit)? = null,
    onBackProgress: ((Float) -> Unit)? = null,
    onBackCancelled: (() -> Unit)? = null,
    triggerIcon: ImageVector? = null,
    triggerIconSize: Dp = 23.dp,
    triggerContentDescription: String? = null,
    onExpand: (() -> Unit)? = null,
    // 整块控件随所在顶栏一起平移：非当前页时被移出屏幕外
    offsetPx: () -> Offset = { Offset.Zero },
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    // 与顶栏液态玻璃按钮同色：收起态即那颗按钮，形变过程中玻璃不突变
    val containerColor = if (isLightTheme) Color(0xFFF7F7F7).copy(0.76f)
        else Color(0xFF242424).copy(0.84f)
    val chromeLens = AppMaterialSettings.chromeLensEnabled()
    val triggerIconTint = if (isLightTheme) Color.Black.copy(0.85f) else Color.White.copy(0.85f)
    // 锚点迁移进度：比尺寸更快到 1 → 控件先"移到面板中心"，尺寸再跟上（还原原来的手感）
    val originProgress = remember { Animatable(0f) }
    // 退出位移回弹：1 →(欠阻尼)→ 0，越过 0 的那段取出来当脉冲，让控件越过终点再弹回
    val settleBounce = remember { Animatable(1f) }

    if (show && onDismiss != null) {
        // 预测性返回：手势进度把 fraction 从 1 拉回 0（容器缩回圆形按钮），取消回弹恢复
        val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        val backProgress = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()

        NavigationBackHandler(
            state = navigationEventState,
            isBackEnabled = show,
            onBackCancelled = {
                onBackCancelled?.invoke()
                coroutineScope.launch {
                    if (backProgress.value > 0f) {
                        fraction.animateTo(1f, animationSpec = tween(150))
                        originProgress.animateTo(1f, animationSpec = tween(150))
                        backProgress.snapTo(0f)
                    }
                }
            },
            onBackCompleted = {
                onDismiss()
            },
        )

        LaunchedEffect(Unit) {
            snapshotFlow { navigationEventState.transitionState }
                .collect { transitionState ->
                    if (
                        transitionState is NavigationEventTransitionState.InProgress &&
                        transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                    ) {
                        if (PredictiveBackSettings.enabled) {
                            val progress = transitionState.latestEvent.progress
                            backProgress.snapTo(progress)
                            onBackProgress?.invoke(progress)
                            fraction.snapTo(1f - progress)
                            originProgress.snapTo(1f - progress)
                        }
                    }
                }
        }
    }

    LaunchedEffect(show) {
        if (show) {
            // 尺寸：沿用原来的缩放曲线
            launch {
                fraction.animateTo(
                    1f,
                    spring(dampingRatio = 0.78f, stiffness = 240f, visibilityThreshold = 0.0001f)
                )
            }
            // 锚点迁移：更快到中心
            launch {
                originProgress.animateTo(
                    1f,
                    spring(dampingRatio = 0.78f, stiffness = 500f, visibilityThreshold = 0.0001f)
                )
            }
            // 进入不回弹
            settleBounce.snapTo(1f)
        } else {
            val exitEasing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
            launch {
                fraction.animateTo(
                    0f,
                    spring(dampingRatio = 0.85f, stiffness = 650f, visibilityThreshold = 0.0001f)
                )
            }
            // 退出位移回弹：独立一条欠阻尼 spring，与尺寸 spring 解耦
            launch {
                settleBounce.snapTo(1f)
                settleBounce.animateTo(
                    0f,
                    spring(dampingRatio = 0.5f, stiffness = 240f, visibilityThreshold = 0.0001f)
                )
            }
            // 这里必须"等"动画跑完（不能立刻 snapTo，否则会把上面的动画取消掉）；
            // 用锚点 tween 兜住，结束后再收尾
            originProgress.animateTo(0f, tween(340, easing = exitEasing))
            fraction.snapTo(0f)
            originProgress.snapTo(0f)
        }
    }

    // 读 fraction 驱动尺寸；不钳到 1，保留 spring 过冲（末尾回弹）
    val f = fraction.value
    // 面板自然高度：由容器内的内容以全尺寸测量得到，作为高度插值终点
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val targetHeight = if (panelHeightPx > 0) {
        with(LocalDensity.current) { panelHeightPx.toDp() }
    } else {
        ButtonDiameter
    }
    val width = lerp(ButtonDiameter, PanelWidth, f)
    val height = lerp(ButtonDiameter, targetHeight, f)
    // 内容随容器等比缩放（还原原来"内容有缩放"的手感），同样保留过冲
    val contentScale = (width.value / PanelWidth.value).coerceAtLeast(0f)

    Box(
        modifier = modifier.padding(ShadowPadding)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    val o = offsetPx()
                    // 精确还原原实现：整块绕"从右上角 (1,0) 迁到正中心 (0.5,0.5)"的锚点缩放时，
                    // 盒子右上角相对固定锚点的位移 = ( -0.5·W·p·(1-s), +0.5·H·p·(1-s) )
                    // p=originProgress、s=当前宽/面板宽；两端位移均归零
                    val p = originProgress.value
                    val s = size.width / PanelWidth.toPx()
                    val k = 0.5f * p * (1f - s)
                    // 退出位移回弹：取 settleBounce 越过 0 的部分（负值）当脉冲，
                    // 沿收回方向（右上）越过终点再弹回；进入时 pulse 恒为 0
                    val pulse = (-settleBounce.value).coerceAtLeast(0f)
                    val bouncePx = pulse * 20f * density
                    translationX = o.x - PanelWidth.toPx() * k + bouncePx
                    translationY = o.y + targetHeight.toPx() * k - bouncePx
                }
                .size(width = width, height = height)
                .drawBehind {
                    // 阴影随盒子尺寸/圆角一起变：收起时是圆形的按钮投影
                    val baseAlpha = if (isLightTheme) 0x1E else 0x2E
                    val blurRadius = 16f * density
                    val r = PanelCornerRadius.toPx()
                    val nativePath = android.graphics.Path().apply {
                        addRoundRect(
                            0f, 0f, size.width, size.height, r, r,
                            android.graphics.Path.Direction.CW
                        )
                    }
                    val paint = Paint().apply {
                        color = android.graphics.Color.argb(baseAlpha, 0, 0, 0)
                        maskFilter = BlurMaskFilter(
                            blurRadius.coerceAtLeast(0.1f),
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPath(nativePath, paint)
                    }
                }
                .clip(ContinuousRoundedRectangle(PanelCornerRadius))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousRoundedRectangle(PanelCornerRadius) },
                    // 与顶栏液态玻璃按钮同一套采样效果
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        if (chromeLens) lens(8.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = {
                        drawRect(containerColor)
                    }
                )
                .edgeLight(
                    shape = ContinuousRoundedRectangle(PanelCornerRadius),
                    edgeLight = rememberDefaultEdgeLight(baseColor = containerColor)
                )
        ) {
            // 菜单内容：按全尺寸铺开、在容器里居中并按中心缩放，随容器长大被逐步裁出来
            Column(
                modifier = Modifier
                    // 居中：缩放中心始终 = 当前卡片整体的正中心
                    .align(Alignment.Center)
                    .requiredWidth(PanelWidth)
                    .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                    .onSizeChanged { panelHeightPx = it.height }
                    .graphicsLayer {
                        alpha = f.coerceIn(0f, 1f)
                        scaleX = contentScale
                        scaleY = contentScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .padding(vertical = 8.dp)
            ) {
                content()
            }

            // 收起态的图标：就在同一个玻璃盒里，随盒子长大淡出
            if (triggerIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(ButtonDiameter)
                        .graphicsLayer { alpha = (1f - f * 2.5f).coerceIn(0f, 1f) }
                        .clickable(enabled = !show && onExpand != null) { onExpand?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = triggerIcon,
                        contentDescription = triggerContentDescription,
                        modifier = Modifier.size(triggerIconSize),
                        tint = triggerIconTint,
                    )
                }
            }
        }
    }
}

@Composable
fun LiquidGlassDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    val isLightTheme = !isAppDarkTheme()
    val textColor = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFE8E4DE)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(ContinuousRoundedRectangle(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.5.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.size(10.dp))
            }
            Text(
                text = text,
                fontSize = 15.6.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}