package com.haooz.softglass.shape

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

typealias PathSegments = List<PathSegment>

fun PathSegments.toPath(): Path = Path().apply {
    if (isEmpty()) return@apply
    val start = first().from
    moveTo(start.x.toFloat(), start.y.toFloat())
    forEach { it.drawTo(this) }
}

@Immutable
sealed interface PathSegment {
    val from: Point
    val to: Point
    fun drawTo(path: Path)

    data class Line(override val from: Point, override val to: Point) : PathSegment {
        override fun drawTo(path: Path) {
            path.lineTo(to.x.toFloat(), to.y.toFloat())
        }
    }

    data class Arc(
        val center: Point,
        val radius: Double,
        val startAngle: Double,
        val sweepAngle: Double,
    ) : PathSegment {
        override val from: Point
            get() = Point(center.x + cos(startAngle) * radius, center.y + sin(startAngle) * radius)
        override val to: Point
            get() = Point(
                center.x + cos(startAngle + sweepAngle) * radius,
                center.y + sin(startAngle + sweepAngle) * radius,
            )

        override fun drawTo(path: Path) {
            val count = ceil(abs(sweepAngle) / (PI * 0.5)).toInt().coerceAtLeast(1)
            val step = sweepAngle / count
            val k = 4.0 / 3.0 * tan(step / 4.0)
            var angle = startAngle
            repeat(count) {
                val next = angle + step
                path.cubicTo(
                    (center.x + radius * (cos(angle) - k * sin(angle))).toFloat(),
                    (center.y + radius * (sin(angle) + k * cos(angle))).toFloat(),
                    (center.x + radius * (cos(next) + k * sin(next))).toFloat(),
                    (center.y + radius * (sin(next) - k * cos(next))).toFloat(),
                    (center.x + radius * cos(next)).toFloat(),
                    (center.y + radius * sin(next)).toFloat(),
                )
                angle = next
            }
        }
    }

    data class Circle(val center: Point, val radius: Double) : PathSegment {
        override val from: Point get() = Point(center.x + radius, center.y)
        override val to: Point get() = from

        override fun drawTo(path: Path) {
            path.addOval(
                Rect(
                    (center.x - radius).toFloat(),
                    (center.y - radius).toFloat(),
                    (center.x + radius).toFloat(),
                    (center.y + radius).toFloat(),
                )
            )
        }
    }

    data class Cubic(val p0: Point, val p1: Point, val p2: Point, val p3: Point) : PathSegment {
        override val from: Point get() = p0
        override val to: Point get() = p3

        override fun drawTo(path: Path) {
            path.cubicTo(
                p1.x.toFloat(), p1.y.toFloat(),
                p2.x.toFloat(), p2.y.toFloat(),
                p3.x.toFloat(), p3.y.toFloat(),
            )
        }
    }
}

inline fun buildPathSegments(block: PathSegmentsBuilder.() -> Unit): PathSegments =
    PathSegmentsBuilder().apply(block).build()

class PathSegmentsBuilder {
    private var startPoint = Point.Zero
    private var currentPoint = Point.Zero
    private var didMove = false
    private val segments = mutableListOf<PathSegment>()

    fun moveTo(x: Double, y: Double) {
        check(!didMove) { "moveTo can only be called once at the beginning of the path" }
        didMove = true
        startPoint = Point(x, y)
        currentPoint = startPoint
    }

    fun lineTo(x: Double, y: Double) {
        val segment = PathSegment.Line(currentPoint, Point(x, y))
        segments += segment
        currentPoint = segment.to
    }

    fun arcTo(center: Point, radius: Double, startAngle: Double, sweepAngle: Double) {
        val segment = PathSegment.Arc(center, radius, startAngle, sweepAngle)
        segments += segment
        currentPoint = segment.to
    }

    fun cubicTo(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) {
        val segment = PathSegment.Cubic(
            currentPoint, Point(x1, y1), Point(x2, y2), Point(x3, y3),
        )
        segments += segment
        currentPoint = segment.to
    }

    fun close() {
        val segment = PathSegment.Line(currentPoint, startPoint)
        segments += segment
        currentPoint = segment.to
    }

    fun build(): PathSegments = segments.toList()
}
