package com.haooz.chedule.ui.activities.classschedule

import com.haooz.chedule.data.model.SpecialBlock

/** 特殊时间块在整列时间轴上的位置（单位 dp）。 */
data class SpecialGridBand(
    val top: Float,
    val height: Float,
    val name: String,
    val startTime: String,
    val endTime: String,
    val blockId: Long = 0L,
)

/** 课表纵向几何；长度单位均为 dp，[sectionTop] 的键是全局节次号 1..n。 */
data class SpecialGridLayout(
    val totalHeight: Float,
    val sectionTop: Map<Int, Float>,
    val dividerY: List<Float>,
    val specialBands: List<SpecialGridBand>,
)

/** 极短特殊块的最小可见高度。 */
private const val MIN_SPECIAL_HEIGHT_DP = 32f

/** "HH:mm" → 当日分钟数；无法解析返回 -1。 */
internal fun parseClockMinutes(time: String?): Int {
    if (time.isNullOrBlank()) return -1
    val parts = time.split(":")
    if (parts.size != 2) return -1
    val hour = parts[0].toIntOrNull() ?: return -1
    val minute = parts[1].toIntOrNull() ?: return -1
    return hour * 60 + minute
}

private data class SectionTime(val start: Int, val end: Int, val section: Int)

/**
 * 把「节次 + 特殊时间块」压成一份纵向几何。
 *
 * 节次默认按固定高度排列，特殊块按起止时间插值成浮层并挤占下方节次。块锚定在
 * 「它压到的第一个节次之前」，保证块永远落在节次边界上、不会盖住课程卡。
 */
fun computeSpecialGridLayout(
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    specialBlocks: List<SpecialBlock>,
    sectionTimes: Map<Int, String>,
    cardHeightPerSection: Float,
    dividerGap: Int,
): SpecialGridLayout {
    val totalSections = morningSections + afternoonSections + eveningSections
    val dividerAfter = setOf(morningSections, morningSections + afternoonSections)

    val originalTop = HashMap<Int, Float>(totalSections)
    var cursor = 0f
    for (section in 1..totalSections) {
        originalTop[section] = cursor
        cursor += cardHeightPerSection
        if (section in dividerAfter) cursor += dividerGap
    }

    val sectionTimesOrdered = buildList {
        for (section in 1..totalSections) {
            val raw = sectionTimes[section] ?: continue
            val parts = raw.split("-")
            if (parts.size != 2) continue
            val start = parseClockMinutes(parts[0])
            val end = parseClockMinutes(parts[1])
            if (start >= 0 && end >= 0) add(SectionTime(start, end, section))
        }
    }

    // 节次起止点即时间锚点：节间与两端按边缘斜率延伸，课间时长也成比例。
    val anchors = buildList {
        sectionTimesOrdered.forEach { info ->
            val top = originalTop[info.section] ?: 0f
            add(info.start to top)
            add(info.end to top + cardHeightPerSection)
        }
    }.sortedBy { it.first }.fold(mutableListOf<Pair<Int, Float>>()) { unique, anchor ->
        // 连续节次 end == 下节 start，去重避免零宽插值区间
        if (unique.isEmpty() || unique.last().first != anchor.first) unique.add(anchor)
        unique
    }

    fun timeToY(minutes: Int): Float {
        if (anchors.isEmpty()) return 0f
        if (minutes <= anchors.first().first) {
            val a0 = anchors[0]
            val a1 = anchors.getOrElse(1) { a0 }
            val rate = if (a1.first > a0.first) (a1.second - a0.second) / (a1.first - a0.first) else 0f
            return a0.second + (minutes - a0.first) * rate
        }
        if (minutes >= anchors.last().first) {
            val a1 = anchors.last()
            val a0 = anchors.getOrElse(anchors.lastIndex - 1) { a1 }
            val rate = if (a1.first > a0.first) (a1.second - a0.second) / (a1.first - a0.first) else 0f
            return a1.second + (minutes - a1.first) * rate
        }
        for (i in 1..anchors.lastIndex) {
            if (minutes <= anchors[i].first) {
                val a0 = anchors[i - 1]
                val a1 = anchors[i]
                val span = a1.first - a0.first
                val t = if (span > 0) (minutes - a0.first).toFloat() / span else 0f
                return a0.second + (a1.second - a0.second) * t
            }
        }
        return anchors.last().second
    }

    // 块顶落在第 g 节区间内就锚到 g；晚于最后一节则贴表底依次堆叠
    fun anchorSectionOf(rawTop: Float): Int {
        for (section in 1..totalSections) {
            if (rawTop < (originalTop[section] ?: 0f) + cardHeightPerSection) return section
        }
        return totalSections + 1
    }

    data class Band(val rawIndex: Int, val anchorSection: Int, val rawTop: Float, val height: Float)

    // 高度按真实时长换算，避免被午/晚休空隙压缩
    val refCardMinutes = sectionTimesOrdered.firstOrNull()
        ?.let { (it.end - it.start).coerceAtLeast(1) } ?: 45
    val sortedBands = specialBlocks.mapIndexed { index, block ->
        val start = parseClockMinutes(block.startTime)
        val end = parseClockMinutes(block.endTime)
        val rawTop = timeToY(start)
        val rawHeight = if (end > start) {
            (end - start) * (cardHeightPerSection / refCardMinutes)
        } else {
            0f
        }
        Band(index, anchorSectionOf(rawTop), rawTop, maxOf(rawHeight, MIN_SPECIAL_HEIGHT_DP))
    }.sortedBy { it.rawTop }

    val sectionTop = HashMap<Int, Float>(totalSections)
    val bandTop = HashMap<Int, Float>(sortedBands.size)
    var y = 0f
    for (section in 1..totalSections) {
        sortedBands.forEach { band ->
            if (band.anchorSection != section) return@forEach
            bandTop[band.rawIndex] = y
            y += band.height
        }
        sectionTop[section] = y
        y += cardHeightPerSection
        if (section in dividerAfter) y += dividerGap
    }
    // 最后一节后已补过分界缝时退回一道，避免表底悬空
    val baseTotal = if (totalSections > 0 && totalSections in dividerAfter) y - dividerGap else y
    var tailY = baseTotal
    sortedBands.forEach { band ->
        if (band.anchorSection != totalSections + 1) return@forEach
        bandTop[band.rawIndex] = tailY
        tailY += band.height
    }

    val dividers = dividerAfter.map { section -> (sectionTop[section] ?: 0f) + cardHeightPerSection }

    @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
    val specialBands = sortedBands.map { band ->
        val block = specialBlocks[band.rawIndex]
        // Gson 旁路可能留下 null 字段
        SpecialGridBand(
            top = (bandTop[band.rawIndex] ?: 0f).coerceAtLeast(0f),
            height = band.height,
            name = block.name ?: "",
            startTime = block.startTime ?: "08:00",
            endTime = block.endTime ?: "08:40",
            blockId = block.id,
        )
    }

    val bandBottom = specialBands.maxOfOrNull { it.top + it.height } ?: 0f
    return SpecialGridLayout(
        totalHeight = maxOf(baseTotal, bandBottom),
        sectionTop = sectionTop,
        dividerY = dividers,
        specialBands = specialBands,
    )
}
