package top.yukonga.miuix.kmp.interactive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.LocalForcedDarkTheme
import top.yukonga.miuix.kmp.theme.rememberAppSettingDark

// ── 形变参数（必须尺寸无关） ──────────────────────────────────────────
private const val DragStretchRatio = 0.045f
private const val DefaultTranslationDamping = 0.08f
private const val DefaultPressScaleDp = 4f
private const val VerticalDragRefScale = 0.6f
private const val AspectPenaltyFloor = 0.97f

/** 玻璃控件档位。 */
const val GlassDragStretchRatio = 0.012f
const val GlassDragTranslationDamping = 0.032f
const val GlassDragPressScaleDp = 2f

private const val DragSelectReadyFraction = 0.98f
private val ScrollTapSlop = 12.dp

/** 遮罩形态：Fade 逐项淡变；Sliding 面板层一整块连贯移动。 */
enum class DropdownMaskMode { Fade, Sliding }

private val DropdownMaskSpring =
    spring<Float>(dampingRatio = 0.85f, stiffness = 700f, visibilityThreshold = 0.01f)
private const val DropdownMaskFadeInMs = 90
/** 淡出要覆盖「一路滑到边界项」的弹簧行程。 */
private const val DropdownMaskFadeOutMs = 220

data class DragTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
)

/** 按压缩放 + 沿拖动方向拉伸 + tanh 阻尼位移。penalty 只乘拉伸项。
 * ⚠️ 天生长条的控件 `shapeAspectRatio` 必须传 1f，否则纵向拉伸被砍没。 */
fun computeDragTransform(
    width: Float,
    height: Float,
    fraction: Float,
    pressProgress: Float,
    offset: Offset,
    density: Density,
    shapeAspectRatio: Float? = null,
    stretchRatio: Float = DragStretchRatio,
    translationDamping: Float = DefaultTranslationDamping,
    pressScaleDp: Float = DefaultPressScaleDp,
): DragTransform {
    val selfH = height.coerceAtLeast(1f)
    val minDim = minOf(width, selfH).coerceAtLeast(1f)
    val pressScale = 1f + with(density) { pressScaleDp.dp.toPx() } / selfH * pressProgress.coerceAtLeast(0f)
    val refY = minDim * (1f - (1f - VerticalDragRefScale) * fraction.coerceIn(0f, 1f))
    val base = shapeAspectRatio
    val penaltyX = (base ?: width / selfH).coerceAtMost(AspectPenaltyFloor)
    val penaltyY = (base?.let { 1f / it } ?: (selfH / width)).coerceAtMost(AspectPenaltyFloor)
    val angle = atan2(offset.y, offset.x)
    return DragTransform(
        scaleX = pressScale + stretchRatio * abs(cos(angle) * offset.x / minDim) * penaltyX,
        scaleY = pressScale + stretchRatio * abs(sin(angle) * offset.y / refY) * penaltyY,
        translationX = minDim * tanh(translationDamping * offset.x / minDim),
        translationY = minDim * tanh(translationDamping * offset.y / minDim),
    )
}

/** 滑选轴向。登记 / 命中 / 手势三处同轴。 */
enum class DragSelectAxis { Vertical, Horizontal }

/** 菜单项登记信息。 */
class DropdownPanelEntry internal constructor() {
    /** 沿滑选轴的区间（手势面板局部坐标，hitTest 用）。 */
    internal var start = 0f
    internal var end = 0f
    /** 相对遮罩容器的矩形（绘制用）。纯布局坐标，父层形变自动带动。 */
    internal var maskLeft = 0f
    internal var maskTop = 0f
    internal var maskRight = 0f
    internal var maskBottom = 0f
    internal var coordinates: LayoutCoordinates? = null
    internal var action: (() -> Unit)? = null
    internal var enabled = true
}

/** 滑选状态。构造器 public：创建方在模块外。 */
class DropdownPanelDragSelectState(
    val axis: DragSelectAxis = DragSelectAxis.Vertical,
    val maskMode: DropdownMaskMode = DropdownMaskMode.Fade,
) {
    private val entries = mutableStateListOf<DropdownPanelEntry>()

    /** 命中项；null = 松手不执行。 */
    var selected by mutableStateOf<DropdownPanelEntry?>(null)
        internal set

    /** Sliding 的几何目标。滑出面板时钳到最近项；clear() 不清，淡出不打断滑动。 */
    var maskTarget by mutableStateOf<DropdownPanelEntry?>(null)
        internal set

    /** 一屏装不下时禁用跟手滑选，纵向手势归 verticalScroll。 */
    var fitsOnScreen by mutableStateOf(true)
        internal set

    var panelTopInRoot by mutableFloatStateOf(0f)
    var panelLeftInRoot by mutableFloatStateOf(0f)

    /** Sliding 遮罩所在容器的坐标。普通字段，不进 snapshot。 */
    internal var maskContainer: LayoutCoordinates? = null

    internal fun register(entry: DropdownPanelEntry) {
        if (!entries.contains(entry)) entries.add(entry)
    }

    internal fun unregister(entry: DropdownPanelEntry) {
        entries.remove(entry)
        if (selected === entry) selected = null
    }

    internal fun updateBounds(entry: DropdownPanelEntry, start: Float, end: Float) {
        entry.start = start
        entry.end = end
    }

    /** localPositionOf 只走布局链，不含 graphicsLayer 矩阵，形变时结果恒定。 */
    internal fun updateItemRect(entry: DropdownPanelEntry) {
        val c = entry.coordinates ?: return
        val container = maskContainer ?: return
        if (!c.isAttached || !container.isAttached) return
        val pos = container.localPositionOf(c, Offset.Zero)
        entry.maskLeft = pos.x
        entry.maskTop = pos.y
        entry.maskRight = pos.x + c.size.width
        entry.maskBottom = pos.y + c.size.height
    }

    /** 容器坐标就绪后全量补算：菜单项与遮罩的回调先后顺序不保证。 */
    internal fun updateMaskContainer(container: LayoutCoordinates) {
        maskContainer = container
        for (entry in entries) updateItemRect(entry)
    }

    internal fun hitTest(position: Float): DropdownPanelEntry? {
        val hit = entries.firstOrNull { it.enabled && position >= it.start && position < it.end }
        selected = hit
        maskTarget = hit ?: nearestEntry(position)
        return hit
    }

    /** 未命中时钳到最近项：滑到底 = 末项、滑到顶 = 首项。 */
    private fun nearestEntry(position: Float): DropdownPanelEntry? {
        var nearest: DropdownPanelEntry? = null
        var best = Float.MAX_VALUE
        for (entry in entries) {
            if (!entry.enabled) continue
            val distance = abs((entry.start + entry.end) * 0.5f - position)
            if (distance < best) {
                best = distance
                nearest = entry
            }
        }
        return nearest
    }

    internal fun clear() {
        selected = null
    }
}

val LocalDropdownPanelDragSelect =
    compositionLocalOf<DropdownPanelDragSelectState?> { null }

/** 面板唯一手势。
 * ⚠️ Sliding 模式还要另挂 [dropdownPanelSlidingMask]。
 * ⚠️ 菜单项不许再挂 clickable。
 * ⚠️ `fraction` 必须现读：`pointerInput(Unit)` 只跑一次。 */
fun Modifier.dropdownPanelDragSelect(
    state: DropdownPanelDragSelectState,
    fraction: () -> Float,
    hapticFeedback: HapticFeedback,
): Modifier = composed {
    dragSelectGestures(state, fraction, hapticFeedback)
}

/** Sliding 遮罩：画在内容之上。
 * ⚠️ 必须挂在**装菜单项的容器**上：矩形取容器局部坐标，父层 graphicsLayer
 * 会把遮罩与菜单项一起变换，draw 期不做任何 root 换算。
 * 几何弹簧与透明度是两条独立 flow，组合期不订阅命中态。 */
fun Modifier.dropdownPanelSlidingMask(
    state: DropdownPanelDragSelectState,
    shape: Shape? = null,
): Modifier = composed {
    if (state.maskMode != DropdownMaskMode.Sliding) return@composed Modifier

    val maskStart = remember { Animatable(0f) }
    val maskEnd = remember { Animatable(0f) }
    val maskAlpha = remember { Animatable(0f) }
    /** 非滑选轴边界（容器局部坐标）。普通数组，不进 snapshot。 */
    val maskCross = remember { floatArrayOf(0f, 0f) }

    // 几何：null 时不动，已启动的弹簧继续跑到边界项。
    LaunchedEffect(state) {
        snapshotFlow { state.maskTarget }.collect { target ->
            if (target == null) return@collect
            val horizontal = state.axis == DragSelectAxis.Horizontal
            val s = if (horizontal) target.maskLeft else target.maskTop
            val e = if (horizontal) target.maskRight else target.maskBottom
            // 容器局部坐标恒定，冻结安全；draw 期不读任何 snapshot state
            maskCross[0] = if (horizontal) target.maskTop else target.maskLeft
            maskCross[1] = if (horizontal) target.maskBottom else target.maskRight
            if (maskAlpha.value < 0.01f) {
                maskStart.snapTo(s)
                maskEnd.snapTo(e)
            } else {
                launch { maskStart.animateTo(s, DropdownMaskSpring) }
                launch { maskEnd.animateTo(e, DropdownMaskSpring) }
            }
        }
    }
    // 透明度：独立一条 flow，取消不会动到几何。
    LaunchedEffect(state) {
        snapshotFlow { state.selected != null }.collectLatest { visible ->
            maskAlpha.animateTo(
                if (visible) 1f else 0f,
                tween(if (visible) DropdownMaskFadeInMs else DropdownMaskFadeOutMs),
            )
        }
    }

    // 遮罩画在内容之上：DropdownImpl 的项自带不透明白底，画下面会被吞。
    val appDark = LocalForcedDarkTheme.current ?: rememberAppSettingDark()
    val maskColor = dropdownPanelEntryHighlightColor(appDark)
    val maskShape = remember(shape) { shape ?: ContinuousRoundedRectangle(17.dp) }
    Modifier
        .onGloballyPositioned { state.updateMaskContainer(it) }
        .drawWithContent {
            drawContent()
            val a = maskAlpha.value
            if (a <= 0f) return@drawWithContent
            val rect = if (state.axis == DragSelectAxis.Horizontal) {
                Rect(maskStart.value, maskCross[0], maskEnd.value, maskCross[1])
            } else {
                Rect(maskCross[0], maskStart.value, maskCross[1], maskEnd.value)
            }
            drawShapedRect(rect, maskShape, maskColor.copy(alpha = maskColor.alpha * a))
        }
}

/** 按下 → 命中 → 滑选 → 松手执行。收起态不接管；装不下时只补点选。 */
private fun Modifier.dragSelectGestures(
    state: DropdownPanelDragSelectState,
    fraction: () -> Float,
    hapticFeedback: HapticFeedback,
): Modifier = pointerInput(Unit) {
    val scrollSlop = ScrollTapSlop.toPx()
    val axisPosition: (Offset) -> Float =
        if (state.axis == DragSelectAxis.Horizontal) ({ it.x }) else ({ it.y })
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (fraction() < DragSelectReadyFraction) return@awaitEachGesture
        if (!state.fitsOnScreen) {
            state.hitTest(axisPosition(down.position))
            var scrolled = false
            while (true) {
                val change = awaitPointerEvent()
                    .changes.fastFirstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (abs(change.position.y - down.position.y) > scrollSlop ||
                    abs(change.position.x - down.position.x) > scrollSlop
                ) {
                    scrolled = true
                    break
                }
            }
            val tapped = state.selected
            state.clear()
            if (!scrolled) tapped?.action?.invoke()
            return@awaitEachGesture
        }
        down.consume()
        var lastHit = state.hitTest(axisPosition(down.position))
        while (true) {
            val change = awaitPointerEvent()
                .changes.fastFirstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val hit = state.hitTest(axisPosition(change.position))
            if (hit != null && hit !== lastHit) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHit = hit
            change.consume()
        }
        val hit = state.selected
        state.clear()
        hit?.action?.invoke()
    }
}

/** 面板整体形变。挂在玻璃壳与内容层的共同父层。 */
fun Modifier.dropdownPanelDragTransform(
    fraction: () -> Float,
    pressProgress: () -> Float,
    dragOffset: () -> Offset,
): Modifier = composed {
    graphicsLayer {
        val t = computeDragTransform(
            width = size.width,
            height = size.height,
            fraction = fraction(),
            pressProgress = pressProgress(),
            offset = dragOffset(),
            density = this,
            stretchRatio = GlassDragStretchRatio,
            translationDamping = GlassDragTranslationDamping,
            pressScaleDp = GlassDragPressScaleDp,
        )
        scaleX = t.scaleX
        scaleY = t.scaleY
        translationX = t.translationX
        translationY = t.translationY
    }
}

/** 命中遮罩底色（App 主题，不是系统深浅色）。 */
private fun dropdownPanelEntryHighlightColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.06f)

/** 按 shape 裁矩形填色。Fade / Sliding 共用。 */
private fun DrawScope.drawShapedRect(rect: Rect, shape: Shape, color: Color) {
    val w = rect.width
    val h = rect.height
    if (w <= 0f || h <= 0f) return
    val outline = shape.createOutline(Size(w, h), layoutDirection, this)
    withTransform({ translate(rect.left, rect.top) }) {
        when (outline) {
            is Outline.Rectangle ->
                clipRect(0f, 0f, w, h) { drawRect(color) }
            is Outline.Rounded ->
                clipPath(Path().apply { addRoundRect(outline.roundRect) }) { drawRect(color) }
            is Outline.Generic ->
                clipPath(Path().apply { addPath(outline.path) }) { drawRect(color) }
        }
    }
}

/** 菜单项：登记区间 + 容器局部矩形 + Fade 遮罩。
 * ⚠️ 必须放在项底色 drawBehind 之后，否则被白底吞掉。
 * ⚠️ Sliding 下只登记，遮罩由容器层统一画。
 * @param state null = 取 [LocalDropdownPanelDragSelect]。必须排在 action 之前（尾随 lambda）。
 * @param selected 静止态是否显示本项遮罩。 */
@Composable
fun Modifier.dropdownPanelEntry(
    enabled: Boolean,
    state: DropdownPanelDragSelectState? = null,
    highlightPadding: PaddingValues = PaddingValues(0.dp),
    highlightShape: Shape = RectangleShape,
    selected: Boolean = false,
    action: () -> Unit,
): Modifier = composed {
    val dragSelect = state ?: LocalDropdownPanelDragSelect.current
    val entry = remember(dragSelect) { DropdownPanelEntry() }
    DisposableEffect(dragSelect, entry) {
        dragSelect?.register(entry)
        onDispose { dragSelect?.unregister(entry) }
    }
    SideEffect {
        entry.action = action
        entry.enabled = enabled
    }
    val registered = this.onGloballyPositioned {
        val dragState = dragSelect ?: return@onGloballyPositioned
        val bounds = it.boundsInRoot()
        if (dragState.axis == DragSelectAxis.Horizontal) {
            val left = bounds.left - dragState.panelLeftInRoot
            dragState.updateBounds(entry, left, left + it.size.width)
        } else {
            val top = bounds.top - dragState.panelTopInRoot
            dragState.updateBounds(entry, top, top + it.size.height)
        }
        entry.coordinates = it
        dragState.updateItemRect(entry)
    }
    if (dragSelect?.maskMode == DropdownMaskMode.Sliding) return@composed registered

    val dragSelected = dragSelect?.selected
    val visible = if (dragSelected != null) dragSelected === entry else selected
    val maskAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "dropdownPanelEntryAlpha",
    )
    val appDark = LocalForcedDarkTheme.current ?: rememberAppSettingDark()
    val highlightColor = dropdownPanelEntryHighlightColor(appDark)
    registered.drawBehind {
        if (maskAlpha <= 0f) return@drawBehind
        val padLeft = highlightPadding.calculateLeftPadding(layoutDirection).toPx()
        val padTop = highlightPadding.calculateTopPadding().toPx()
        val padRight = highlightPadding.calculateRightPadding(layoutDirection).toPx()
        val padBottom = highlightPadding.calculateBottomPadding().toPx()
        drawShapedRect(
            Rect(
                padLeft,
                padTop,
                (size.width - padRight).coerceAtLeast(padLeft),
                (size.height - padBottom).coerceAtLeast(padTop),
            ),
            highlightShape,
            highlightColor.copy(alpha = highlightColor.alpha * maskAlpha),
        )
    }
}