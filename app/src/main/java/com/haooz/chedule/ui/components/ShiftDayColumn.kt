/** 排班课表组件 - 显示排班视图中的日期列 */
package com.haooz.chedule.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.Course

@Composable
fun ShiftDayColumn(
    dayOfWeek: Int,
    allScheduleCourses: Map<String, List<Course>>,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    currentWeek: Int,
    onSlotClick: (dayOfWeek: Int, startSection: Int, courses: List<Pair<String, Course>>) -> Unit = { _, _, _ -> },
    cardHeightPerSection: Float = 54f,
    isTablet: Boolean = false,
    cardCornerRadius: Float = 10f,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val totalHeight = ((morningSections + afternoonSections + eveningSections) * cardHeightPerSection + 24 * 2).toInt()

    val allCourses = mutableListOf<Pair<String, Course>>()
    for ((name, courses) in allScheduleCourses) {
        for (c in courses) {
            if (c.dayOfWeek == dayOfWeek && c.isActiveInWeek(currentWeek)) {
                allCourses.add(name to c)
            }
        }
    }

    fun sectionToY(section: Int): Float {
        return when {
            section <= morningSections -> (section - 1) * cardHeightPerSection
            section <= morningSections + afternoonSections -> morningSections * cardHeightPerSection + 24 + (section - morningSections - 1) * cardHeightPerSection
            else -> morningSections * cardHeightPerSection + 24 + afternoonSections * cardHeightPerSection + 24 + (section - morningSections - afternoonSections - 1) * cardHeightPerSection
        }
    }

    Box(
        modifier = modifier.height(totalHeight.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            var currentOffset = 0f

            for (section in 1..morningSections) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                )
                currentOffset += cardHeightPerSection
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .offset(y = currentOffset.dp)
            )
            currentOffset += 24
            for (section in (morningSections + 1)..(morningSections + afternoonSections)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                )
                currentOffset += cardHeightPerSection
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .offset(y = currentOffset.dp)
            )
            currentOffset += 24
            for (section in (morningSections + afternoonSections + 1)..(morningSections + afternoonSections + eveningSections)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightPerSection.dp)
                        .offset(y = currentOffset.dp)
                )
                currentOffset += cardHeightPerSection
            }
        }

        // 按课表实际节次边界拆段（不是固定每 2 节一组）：
        // · 有 1-1、1-2、1-3 三门课 → 边界在 1|2|3 → 拆成 1+1+1
        // · 有 1-2 和 1-3 两门课     → 边界在 1|2…3 → 拆成 2+1
        // · 只有一门 1-4             → 不拆，整段一张卡
        // 午休/晚休边界强制切开，空段不渲染；同段多门课聚合成一张卡。
        val totalSections = morningSections + afternoonSections + eveningSections
        val bandStarts = listOfNotNull(
            1.takeIf { morningSections > 0 },
            (morningSections + 1).takeIf { afternoonSections > 0 },
            (morningSections + afternoonSections + 1).takeIf { eveningSections > 0 }
        )

        val cuts = sortedSetOf(1)
        for ((_, course) in allCourses) {
            val s = course.startSection
            val e = course.endSection
            if (s in 1..totalSections) cuts.add(s)
            if (e + 1 in 1..totalSections) cuts.add(e + 1)
        }
        // 分段起点不能跨过上午/下午/晚上分界
        for (bandStart in bandStarts) {
            if (bandStart in 1..totalSections) cuts.add(bandStart)
        }

        data class BlockSlot(
            val startSection: Int,
            val endSection: Int,
            val items: List<Pair<String, Course>>
        )

        val cutList = cuts.toList()
        val blockSlots = buildList {
            for (i in cutList.indices) {
                val segStart = cutList[i]
                val segEnd = (cutList.getOrNull(i + 1) ?: (totalSections + 1)) - 1
                if (segStart > segEnd || segStart > totalSections) continue
                val end = minOf(segEnd, totalSections)
                // 段不得跨时段分界（cuts 已含 bandStart，这里再兜底）
                if (bandStarts.any { it > segStart && it <= end }) continue
                val items = allCourses.filter { (_, course) ->
                    course.startSection <= end && course.endSection >= segStart
                }
                if (items.isEmpty()) continue
                add(BlockSlot(segStart, end, items))
            }
        }

        blockSlots.forEach { slot ->
            val span = slot.endSection - slot.startSection + 1
            val cardHeight = span * cardHeightPerSection
            val y = sectionToY(slot.startSection)
            ShiftCell(
                courses = slot.items.distinctBy { it.first },
                isTablet = isTablet,
                cardCornerRadius = cardCornerRadius,
                onClick = { onSlotClick(dayOfWeek, slot.startSection, slot.items) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight.dp)
                    .offset(y = y.dp)
            )
        }
    }
}
