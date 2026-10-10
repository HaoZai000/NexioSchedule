package com.haooz.chedule.ui.components

import com.haooz.chedule.data.SpecialBlock

data class SpecialGridBand(
    val top: Float,
    val height: Float,
    val name: String,
    val startTime: String,
    val endTime: String,
    val blockId: Long = 0L
)

// totalHeight/sectionTop/dividerY 单位均为 dp；sectionTop 键为全局节次号 1..
data class SpecialGridLayout(
    val totalHeight: Float,
    val sectionTop: Map<Int, Float>,
    val dividerY: List<Float>,
    val specialBands: List<SpecialGridBand>
)

// 不足 32dp 按 32dp 渲染，避免极短特殊块不可见
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

// 节次保持固定位置，特殊块按起止时间沿时间轴插值成整条浮层并挤占下方节次
@Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON", "ELVIS_ALWAYS_NULL")
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

    val origSectionTop = mutableMapOf<Int, Float>()
    var cursor = 0f
    for (g in 1..totalSections) {
        origSectionTop[g] = cursor
        cursor += cardHeightPerSection
        if (g in dividerAfter) cursor += dividerGap
    }

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

    // 节起止点作时间锚点，节间/两端按边缘斜率延伸，使课间与课外时间也按时长成比例
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
        // 去重：连续节次 end==下节 start，避免零宽插值区间
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

    /**
     * 块顶落在第 g 节的卡片区间内就锚到 g（插到它前面），落在最后一节课之后返回
     * totalSections + 1（贴表底）。rawTop ≤ 0（早于第一节）自然锚到 1。
     */
    fun anchorSectionOf(rawTop: Float): Int {
        for (g in 1..totalSections) {
            val top = origSectionTop[g] ?: 0f
            if (rawTop < top + cardHeightPerSection) return g
        }
        return totalSections + 1
    }

    // 高度按真实时长×每分钟像素（避免被午休空隙压缩）；像素以第一节时长为基准
    data class Band(val rawIndex: Int, val anchorSection: Int, val rawTop: Float, val height: Float)
    val refCardMinutes = secInfos.firstOrNull()?.let { (it.end - it.start).coerceAtLeast(1) } ?: 45
    val sortedBands = specialBlocks.mapIndexed { i, sp ->
        val cs = parseMinutesHm(sp.startTime)
        val ce = parseMinutesHm(sp.endTime)
        val rawTop = timeToYClamped(cs)
        var h = if (ce > cs) (ce - cs) * (cardHeightPerSection / refCardMinutes) else 0f
        if (h < MIN_SPECIAL_HEIGHT_DP) h = MIN_SPECIAL_HEIGHT_DP
        Band(i, anchorSectionOf(rawTop), rawTop, h)
    }.sortedBy { it.rawTop }

    /**
     * 一次从上到下的扫描排完节次与横带，取代原来「先算节次偏移、再补块位置」的写法。
     *
     * 锚点语义：块插入到**它压到的第一个节次之前**（rawTop 落在第 g 节的卡片区间内
     * 就锚到 g）。于是块永远待在节次边界上，不会骑在课程卡上：
     * 早先按「块顶 ≤ 节次顶才下移」判定，嵌在节次内部的块既不推动那一节（被卡片盖住，
     * 又因为点击层在最上层而抢走那一条的课程点击）。
     */
    val sectionTop = mutableMapOf<Int, Float>()
    val bandTop = HashMap<Int, Float>() // 块在 specialBlocks 里的下标 -> 顶部 y
    var cursorY = 0f
    for (g in 1..totalSections) {
        for (b in sortedBands) {
            if (b.anchorSection != g) continue
            bandTop[b.rawIndex] = cursorY
            cursorY += b.height
        }
        sectionTop[g] = cursorY
        cursorY += cardHeightPerSection
        if (g in dividerAfter) cursorY += dividerGap
    }
    // 末尾不留悬空的分界缝：晚上 0 节时 totalSections 本身就在 dividerAfter 里，
    // 循环会在最后补一道缝，后面接的块会凭空上移一截
    val baseTotal = if (totalSections > 0 && totalSections in dividerAfter) {
        cursorY - dividerGap
    } else {
        cursorY
    }
    // 锚点落在最后一节课之后（totalSections + 1）：贴表底依次往下堆
    var tailY = baseTotal
    for (b in sortedBands) {
        if (b.anchorSection != totalSections + 1) continue
        bandTop[b.rawIndex] = tailY
        tailY += b.height
    }

    val dividers = dividerAfter.map { g -> (sectionTop[g] ?: 0f) + cardHeightPerSection }

    val specialBands = sortedBands.map { b ->
        val sp = specialBlocks[b.rawIndex]
        // Gson 旁路可能留下 null 字段，再兜一层避免非空参数 NPE
        SpecialGridBand(
            (bandTop[b.rawIndex] ?: 0f).coerceAtLeast(0f),
            b.height,
            sp.name ?: "",
            sp.startTime ?: "08:00",
            sp.endTime ?: "08:40",
            sp.id
        )
    }

    val bandBottomMax = specialBands.maxOfOrNull { it.top + it.height } ?: 0f
    val totalHeight = maxOf(baseTotal, bandBottomMax)

    return SpecialGridLayout(totalHeight, sectionTop, dividers, specialBands)
}