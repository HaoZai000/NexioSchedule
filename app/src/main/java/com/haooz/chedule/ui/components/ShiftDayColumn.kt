/** 调课日期列组件 - 显示调课视图中的日期列 */
package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.Course
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ShiftDayColumn(
    dayOfWeek: Int,
    allScheduleCourses: Map<String, List<Course>>,
    scheduleColors: Map<String, Color>,
    morningSections: Int,
    afternoonSections: Int,
    eveningSections: Int,
    currentWeek: Int,
    onSlotClick: (dayOfWeek: Int, startSection: Int, courses: List<Pair<String, Course>>) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val totalHeight = (morningSections + afternoonSections + eveningSections) * 54 + 24 * 2

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
        val sameRange = mutableListOf<Pair<String, Course>>(name to course)
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

    fun sectionToY(section: Int): Int {
        val m = morningSections
        val a = afternoonSections
        return when {
            section <= m -> (section - 1) * 54
            section <= m + a -> m * 54 + 24 + (section - m - 1) * 54
            else -> m * 54 + 24 + a * 54 + 24 + (section - m - a - 1) * 54
        }
    }

    Box(
        modifier = modifier.height(totalHeight.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            var currentOffset = 0

            for (section in 1..morningSections) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .offset(y = currentOffset.dp)
                )
                currentOffset += 54
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .offset(y = currentOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                )
            }
            currentOffset += 24
            for (section in (morningSections + 1)..(morningSections + afternoonSections)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .offset(y = currentOffset.dp)
                )
                currentOffset += 54
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .offset(y = currentOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                )
            }
            currentOffset += 24
            for (section in (morningSections + afternoonSections + 1)..(morningSections + afternoonSections + eveningSections)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .offset(y = currentOffset.dp)
                )
                currentOffset += 54
            }
        }

        for (group in groups) {
            val span = group.endSection - group.startSection + 1
            val cardHeight = span * 54 + (span - 1) * 0
            val y = sectionToY(group.startSection)
            ShiftCell(
                courses = group.items,
                scheduleColors = scheduleColors,
                onClick = { onSlotClick(dayOfWeek, group.startSection, group.items) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight.dp)
                    .offset(y = y.dp)
            )
        }
    }
}
