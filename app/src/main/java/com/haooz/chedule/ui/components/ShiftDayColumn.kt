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

    data class MergedGroup(
        val startSection: Int,
        val endSection: Int,
        val items: List<Pair<String, Course>>
    )

    val groups = mutableListOf<MergedGroup>()
    val used = mutableSetOf<Int>()

    for (i in allCourses.indices) {
        if (i in used) continue
        val (name, course) = allCourses[i]
        val sameRange = mutableListOf(name to course)
        for (j in i + 1 until allCourses.size) {
            if (j in used) continue
            val (name2, course2) = allCourses[j]
            if (course2.startSection == course.startSection && course2.endSection == course.endSection) {
                sameRange.add(name2 to course2)
                used.add(j)
            }
        }
        used.add(i)
        groups.add(MergedGroup(course.startSection, course.endSection, sameRange))
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

        // 排班分组块：早上/下午每 2 个小节为一组，晚上整段为一组
        // （每个块天然含午休/晚休边界，段不会跨分割条）
        val groupBlocks = buildList<Pair<Int, Int>> {
            for (s in 1..morningSections step 2) {
                add(s to minOf(s + 1, morningSections))
            }
            for (s in (morningSections + 1)..(morningSections + afternoonSections) step 2) {
                add(s to minOf(s + 1, morningSections + afternoonSections))
            }
            if (eveningSections > 0) {
                add(
                    (morningSections + afternoonSections + 1) to
                        (morningSections + afternoonSections + eveningSections)
                )
            }
        }

        // 所有课程按组块边界拆分，跨组的课程落到各自块内：
        // 例：1-2 节 → 第一组；1-3 节 → 12 第一组 + 3 第二组；2-3 节 → 2 第一组 + 3 第二组；
        // 1-4 节 → 12 第一组 + 34 第二组；晚上课程无论几节 → 整段一组。
        // 同一组块可能被多个不同小节范围的课程（不同课表的课）命中，
        // 全部聚合为一块一张卡，范围取并集，避免各自渲染互相重叠。
        data class BlockSlot(
            var startSection: Int,
            var endSection: Int,
            val items: MutableList<Pair<String, Course>> = mutableListOf()
        )
        val blockSlots = LinkedHashMap<Pair<Int, Int>, BlockSlot>()
        for (group in groups) {
            for ((blockStart, blockEnd) in groupBlocks) {
                val segStart = maxOf(blockStart, group.startSection)
                val segEnd = minOf(blockEnd, group.endSection)
                if (segStart > segEnd) continue
                val key = blockStart to blockEnd
                val slot = blockSlots.getOrPut(key) { BlockSlot(segStart, segEnd) }
                if (segStart < slot.startSection) slot.startSection = segStart
                if (segEnd > slot.endSection) slot.endSection = segEnd
                slot.items.addAll(group.items)
            }
        }

        blockSlots.values.forEach { slot ->
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
