package com.haooz.chedule.ui.components

import com.haooz.chedule.data.SpecialBlock

/**
 * 特殊课程长条渲染信息（无编号，如早读/大课间/眼保健操）。
 */
data class SpecialGridBand(
    val top: Float,
    val height: Float,
    val name: String,
    val startTime: String,
    val endTime: String
)

/**
 * 含特殊课程块的整周网格几何。
 *
 * @param totalHeight 整列总高度（dp）
 * @param sectionTop   全局绝对节次号(1..) -> 顶部偏移（dp）
 * @param dividerY     午休/晚休分界带的顶部偏移（dp），仅在显示分界线时有值
 * @param specialBands 特殊课程长条的渲染位置（top/height 由时间轴插值得到，dp）
 */
data class SpecialGridLayout(
    val totalHeight: Float,
    val sectionTop: Map<Int, Float>,
    val dividerY: List<Float>,
    val specialBands: List<SpecialGridBand>
)

/** 特殊块高度下限：不足 20dp 时按 20dp 渲染（高度单位与网格一致，为 dp 数值） */
private const val MIN_SPECIAL_HEIGHT_DP = 32f

private fun parseMinutesHm(time: String?): Int {
    if (time.isNullOrBlank()) return -1
    return try {
        val parts = time.split(":")
        if (parts.size != 2) -1 else parts[0].toInt() * 60 + parts[1].toInt()
    } catch (_: Exception) {
        -1
    }
}

/**
 * 计算含特殊课程块的整周网格几何。
 *
 * 特殊课程作为"一整个长条"浮层：节次保持原有固定位置，每个特殊块按自定义起止时间
 * 沿时间轴插值（timeToY）得到纵向起止与整段高度（非一节一节的分段卡片）。
 * 左侧时间列（SectionColumn）会在对应高度处显示其起止时间。
 * 特殊块不计入课程节次号，也不参与课程提醒。
 *
 * @param dividerGap 午休/晚休分界带高度（dp，0 表示不显示）
 */
fun computeSpecialGridLayout(
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    specialBlocks: List<SpecialBlock>,
    sectionTimes: Map<Int, String>,
    cardHeightPerSection: Float,
    dividerGap: Int
): SpecialGridLayout {
    val totalSections = morningSections + afternoonSections + eveningSections
    val dividerAfter = setOf(morningSections, morningSections + afternoonSections)

    // 1) 原始节次几何（不含特殊块避让）：作为“时间 → Y” 插值基准
    val origSectionTop = mutableMapOf<Int, Float>()
    var cursor = 0f
    for (g in 1..totalSections) {
        origSectionTop[g] = cursor
        cursor += cardHeightPerSection
        if (g in dividerAfter) cursor += dividerGap
    }
    val origBottom = cursor

    // 2) 节次时间信息，用于时间插值
    data class SecInfo(val start: Int, val end: Int, val index: Int)
    val secInfos = mutableListOf<SecInfo>()
    for (g in 1..totalSections) {
        val t = sectionTimes[g] ?: continue
        val parts = t.split("-")
        if (parts.size != 2) continue
        val ss = parseMinutesHm(parts[0])
        val se = parseMinutesHm(parts[1])
        if (ss < 0 || se < 0) continue
        secInfos.add(SecInfo(ss, se, g))
    }

    // 构建 “时间 → Y” 锚点骨架：每个节次的起点/终点均有确定 y（对应节次几何 top / top+卡高）。
    // 任意分钟在其相邻锚点间线性插值；早于最早锚点 / 晚于最晚锚点按边缘段斜率延伸。
    // 这样在第一节之前、最后一节之后以及节间空隙中的时间，也能得到与真实时长成比例的 y。
    data class Anchor(val minutes: Int, val y: Float)
    val anchors = mutableListOf<Anchor>()
    for (info in secInfos) {
        val top = origSectionTop[info.index] ?: 0f
        anchors.add(Anchor(info.start, top))
        anchors.add(Anchor(info.end, top + cardHeightPerSection))
    }
    anchors.sortBy { it.minutes }
    val unique = mutableListOf<Anchor>()
    for (a in anchors) {
        // 去重（连续节次 end == 下节 start，y 相同），避免出现零宽插值区间
        if (unique.isEmpty() || unique.last().minutes != a.minutes) unique.add(a)
    }

    fun timeToY(minutes: Int): Float {
        if (unique.isEmpty()) return 0f
        if (minutes <= unique.first().minutes) {
            val a0 = unique[0]
            val a1 = if (unique.size > 1) unique[1] else a0
            val rate = if (a1.minutes > a0.minutes) (a1.y - a0.y) / (a1.minutes - a0.minutes) else 0f
            return a0.y + (minutes - a0.minutes) * rate
        }
        if (minutes >= unique.last().minutes) {
            val a1 = unique.last()
            val a0 = if (unique.size > 1) unique[unique.size - 2] else a1
            val rate = if (a1.minutes > a0.minutes) (a1.y - a0.y) / (a1.minutes - a0.minutes) else 0f
            return a1.y + (minutes - a1.minutes) * rate
        }
        for (i in 1 until unique.size) {
            if (minutes <= unique[i].minutes) {
                val a0 = unique[i - 1]
                val a1 = unique[i]
                val span = a1.minutes - a0.minutes
                val t = if (span > 0) (minutes - a0.minutes).toFloat() / span else 0f
                return a0.y + (a1.y - a0.y) * t
            }
        }
        return unique.last().y
    }

    fun timeToYClamped(minutes: Int): Float = timeToY(minutes)

    // 3) 每个特殊块：位置按时间插值，高度按真实时长 × 每分钟像素（避免被午休等空隙压缩）
    //    每分钟像素以第一节时长为基准
    data class Band(val rawIndex: Int, val rawTop: Float, val height: Float)
    val refCardMinutes = secInfos.firstOrNull()?.let { (it.end - it.start).coerceAtLeast(1) } ?: 45
    val sortedBands = specialBlocks.mapIndexed { i, sp ->
        val cs = parseMinutesHm(sp.startTime)
        val ce = parseMinutesHm(sp.endTime)
        val rawTop = timeToYClamped(cs)
        var h = if (ce > cs) (ce - cs) * (cardHeightPerSection / refCardMinutes) else 0f
        // 高度下限：按起止时间计算，不足 20dp 时按 20dp 渲染，避免“不显示”
        if (h < MIN_SPECIAL_HEIGHT_DP) h = MIN_SPECIAL_HEIGHT_DP
        Band(i, rawTop, h)
    }.sortedBy { it.rawTop }

    // 4) 避让：特殊块在其时间起点处占位，起点位于其下方的节次/时间轴整体下移，互不重叠。
    //    节次 g 的偏移 = 所有“起点不晚于其顶端”的特殊块高度之和
    fun sectionOffset(g: Int): Float {
        var off = 0f
        val top = origSectionTop[g] ?: 0f
        for (b in sortedBands) if (b.rawTop <= top) off += b.height
        return off
    }
    //    特殊块 k 的偏移 = 所有“起点不晚于它”（自身除外）的特殊块高度之和
    fun bandOffset(k: Int): Float {
        var off = 0f
        for (i in 0 until k) if (sortedBands[i].rawTop <= sortedBands[k].rawTop) off += sortedBands[i].height
        return off
    }

    val sectionTop = mutableMapOf<Int, Float>()
    for (g in 1..totalSections) sectionTop[g] = (origSectionTop[g] ?: 0f) + sectionOffset(g)

    val dividers = dividerAfter.map { g -> (sectionTop[g] ?: 0f) + cardHeightPerSection }

    val specialBands = sortedBands.mapIndexed { k, b ->
        val sp = specialBlocks[b.rawIndex]
        // 显示位置钳制到 ≥0（第一节前的特殊块不会被裁出容器），避让排序仍按原始 rawTop
        var displayTop = (b.rawTop + bandOffset(k)).coerceAtLeast(0f)
        // 若特殊块顶部落入某条分界带（午休/晚休）内，则下移到分界带下方，避免与分界线重合。
        // 特殊块落在分界带所代表的大空隙（如午休 12:20-14:00）中时，时间插值会把其顶部
        // 压缩到靠近分界线处，需按用户预期将其置于分界线下方、下午第一节上方。
        for (div in dividers) {
            if (displayTop >= div && displayTop < div + dividerGap) {
                displayTop = div + dividerGap
            }
        }
        SpecialGridBand(displayTop, b.height, sp.name, sp.startTime, sp.endTime)
    }

    val baseTotal = (sectionTop[totalSections] ?: 0f) + cardHeightPerSection
    val bandBottomMax = specialBands.maxOfOrNull { it.top + it.height } ?: 0f
    val totalHeight = maxOf(baseTotal, bandBottomMax)

    return SpecialGridLayout(totalHeight, sectionTop, dividers, specialBands)
}