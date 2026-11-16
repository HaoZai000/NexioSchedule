package com.haooz.chedule.ui.ai

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * 流式文字渲染：整段排版 + 逐字淡入。
 *
 * 底稿按完整文本排版，绘制期裁到已揭示的位置，折行不会来回跳；队尾几个字单独绘制，
 * alpha 由同一份揭示进度在绘制期算出。
 */

/** 队尾逐字淡入的窗口字数。 */
private const val RevealTailChars = 14

/** 一个字从未揭示到完全清晰的过渡字数，必须小于 [RevealTailChars]。 */
private const val RevealFadeChars = 9

/** 每个字的浮现时长（ms）。 */
private const val RevealCharMillis = 26L

/** 单次补字的最长时长（ms）：落后太多时加速追赶，但仍逐字推进。 */
private const val RevealCatchUpMaxMillis = 420L

/**
 * 流式文字。
 *
 * @param revealBase [text] 在全文中的起始下标。传入尾部窗口时必须给：揭示进度按全文累计，
 *   窗口滑动时动画不会重来。
 */
@Composable
internal fun StreamingRevealText(
    text: String,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    revealBase: Int = 0,
) {
    if (text.isEmpty()) return
    // 已揭示字数，按全文口径累计；减去 revealBase 才是本窗口内的位置
    val head = remember { Animatable(0f) }
    val base by rememberUpdatedState(revealBase)
    LaunchedEffect(text.length, revealBase) {
        // 一次涌入的内容越过窗口时直接对齐到窗口起点，避免几百毫秒的空白
        if (head.value < revealBase) head.snapTo(revealBase.toFloat())
        // 多推 RevealFadeChars 个字，保证停下来时队尾文字已完全不透明
        val target = (text.length + revealBase + RevealFadeChars).toFloat()
        if (target <= head.value) return@LaunchedEffect
        val pending = target - head.value
        val millis = (pending * RevealCharMillis).toLong()
            .coerceIn(RevealCharMillis, RevealCatchUpMaxMillis)
        head.animateTo(target, tween(millis.toInt(), easing = LinearEasing))
    }
    // lambda 只建一次，不能捕获 text / revealBase，否则进度会被首帧的值钳死
    val revealed by remember { derivedStateOf { head.value.toInt() - base } }
    val headState = head.asState()
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer(cacheSize = 32)
        val layout = remember(text, style, maxLines, constraints) {
            measurer.measure(
                text = text,
                style = style,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                constraints = constraints,
            )
        }
        val lastLine = layout.lineCount.coerceAtLeast(1) - 1
        // visibleEnd = true：忽略被省略号顶掉的区域
        val visibleEnd = layout.getLineEnd(lastLine, visibleEnd = true).coerceIn(0, text.length)
        // 揭示点不能落在代理对中间，否则 emoji 会被切开
        val tailEnd = text.codePointStart(minOf(revealed, visibleEnd).coerceAtLeast(0))
        val sharpEnd = (tailEnd - RevealTailChars).coerceAtLeast(0)
        val density = LocalDensity.current
        val boxWidth = with(density) { layout.size.width.toDp() }
        // 高度只到已揭示的行，框不会抢在文字前面长高
        val boxHeight = with(density) {
            if (tailEnd <= 0) {
                0.dp
            } else {
                layout.getLineBottom(layout.getLineForOffset(tailEnd - 1)).toDp()
            }
        }
        // 单字底稿：位置来自整段排版，这里只缓存字形
        val charLayouts = remember(measurer, style) { HashMap<String, TextLayoutResult>() }
        // 队尾字的位置与排版有关，先算好；绘制期只算淡入进度
        val tailChars = remember(layout, text, sharpEnd, tailEnd) {
            buildList {
                var index = sharpEnd
                while (index < tailEnd) {
                    val end = text.codePointEnd(index)
                    val piece = text.substring(index, end)
                    if (piece.isNotBlank()) {
                        val charLayout = charLayouts.getOrPut(piece) {
                            measurer.measure(
                                text = piece,
                                style = style,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        // 用基线定位：不受各平台 getBoundingBox 语义与行高影响
                        add(
                            TailChar(
                                layout = charLayout,
                                at = Offset(
                                    layout.getHorizontalPosition(index, usePrimaryDirection = true),
                                    layout.getLineBaseline(layout.getLineForOffset(index)) -
                                        charLayout.firstBaseline,
                                ),
                                index = index,
                            )
                        )
                    }
                    index = end
                }
            }
        }

        Box(modifier = Modifier.width(boxWidth).height(boxHeight)) {
            Canvas(Modifier.requiredSize(boxWidth, boxHeight)) {
                when {
                    // 揭示点已在可视区之外：可见内容都应清晰
                    sharpEnd >= visibleEnd -> drawText(layout)
                    sharpEnd > 0 ->
                        clipPath(revealPath(layout, sharpEnd, text)) { drawText(layout) }
                }
                val tail = headState.value
                for (char in tailChars) {
                    // char.index 是窗口内下标，淡入进度用全文口径
                    val alpha = revealProgress(tail, char.index + revealBase)
                    if (alpha > 0.01f) {
                        drawText(char.layout, topLeft = char.at, alpha = alpha)
                    }
                }
            }
        }
    }
}

/** 队尾一个正在浮现的字：字形与它在整段排版中的落位。 */
private class TailChar(
    val layout: TextLayoutResult,
    val at: Offset,
    val index: Int,
)

/** 一个字的揭示进度 0..1。 */
private fun revealProgress(head: Float, index: Int): Float =
    ((head - index - 1f) / RevealFadeChars).coerceIn(0f, 1f)

private fun String.codePointStart(index: Int): Int =
    if (index in 1 until length && this[index].isLowSurrogate()) index - 1 else index

/** 从 [index] 起的一个字符（emoji 代理对算一个整体）。 */
private fun String.codePointEnd(index: Int): Int =
    if (
        this[index].isHighSurrogate() &&
        index + 1 < length &&
        this[index + 1].isLowSurrogate()
    ) {
        index + 2
    } else {
        index + 1
    }

/** 已揭示部分的裁切路径：前面的整行 + 当前行到该字右缘。 */
private fun revealPath(layout: TextLayoutResult, endExclusive: Int, text: String): Path {
    val path = Path()
    if (endExclusive <= 0) return path
    val last = endExclusive - 1
    val line = layout.getLineForOffset(last)
    val top = layout.getLineTop(line)
    if (line > 0) {
        path.addRect(Rect(0f, 0f, layout.size.width.toFloat(), top))
    }
    path.addRect(
        Rect(
            left = 0f,
            top = top,
            right = layout.revealRightEdge(last, line, text) + 1f,
            bottom = layout.getLineBottom(line),
        )
    )
    return path
}

/**
 * 揭示到 [last] 时该行的右边界。
 *
 * 行尾空格与换行符的字形盒是零宽的：换行代表整行已揭示，行中空格则不能按整行右边界裁，
 * 否则后半行的字会提前放出来再收回，看着像乱闪。
 */
private fun TextLayoutResult.revealRightEdge(last: Int, line: Int, text: String): Float {
    val glyph = getBoundingBox(last)
    if (glyph.width > 0f) return glyph.right
    if (text[last] == '\n') return getLineRight(line)
    // 回退到同行前一个可见字；一个都没有说明这一行还没开始
    var index = last - 1
    while (index >= 0) {
        if (getLineForOffset(index) != line) return getLineLeft(line)
        val box = getBoundingBox(index)
        if (box.width > 0f && !text[index].isWhitespace()) return box.right
        index--
    }
    return getLineLeft(line)
}

/** 去掉 `**加粗**` 与 `` `行内代码` `` 的标记符，正文保留。 */
internal fun String.stripMarkdownEmphasis(): String = this.replace("**", "").replace("`", "")

/**
 * 流式思考的尾部窗口起点：只保留最后一段，尽量从换行处起头。
 *
 * 思考正文一直在追加，预览只有几行；直接排版全文再截断会永远停在开头。
 * 与 [StreamingRevealText] 的 revealBase 配合，窗口前移时逐字动画不会重来。
 */
internal fun String.streamingWindowStart(maxChars: Int = 900): Int {
    if (length <= maxChars) return 0
    val rough = length - maxChars
    val lineBreak = indexOf('\n', rough)
    val start = if (lineBreak in rough until length - 1) lineBreak + 1 else rough
    return codePointStart(start.coerceIn(0, length))
}

/**
 * 按自身高度排版，只占父级允许的高度，底对齐并裁掉上方溢出。
 *
 * 用于思考区「新行把旧行顶上去」的滚动效果。不能用 `wrapContentSize(unbounded = true)`：
 * 它连宽度一起放开，文字会排成一行。
 */
internal fun Modifier.bottomAnchoredClip(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
    )
    val height = placeable.height.coerceAtMost(constraints.maxHeight)
    layout(placeable.width.coerceAtMost(constraints.maxWidth), height) {
        placeable.place(0, height - placeable.height)
    }
}
