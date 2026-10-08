package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.SpecialItem
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 弹窗里周一–周日的格子标签 */
val SPECIAL_WEEK_LABELS = arrayOf("一", "二", "三", "四", "五", "六", "日")

/** 危险操作红，与项目内其它删除入口一致 */
private val SpecialDestructiveColor = Color(0xFFF44336)

/** {1,3,4} → [(1,1), (3,4)]；输入需已排序 */
fun mergeConsecutiveDays(sortedDays: List<Int>): List<Pair<Int, Int>> {
    if (sortedDays.isEmpty()) return emptyList()
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start = sortedDays[0]
    var end = sortedDays[0]
    for (i in 1 until sortedDays.size) {
        if (sortedDays[i] == end + 1) {
            end = sortedDays[i]
        } else {
            ranges.add(start to end)
            start = sortedDays[i]
            end = sortedDays[i]
        }
    }
    ranges.add(start to end)
    return ranges
}

/**
 * 给一批新子块分配**互不相同**的 id。
 *
 * 不要再用 `System.currentTimeMillis()`：同一批 map 里多次调用落在同一毫秒就会撞号，
 * 而删除/编辑都是按 id 过滤的（`items.filter { it.id != editingId }`），
 * 撞号等于「删一个连带删掉另一个、编辑一个吞掉另一个」。
 */
fun nextSpecialItemIds(existing: List<SpecialItem>, count: Int): List<Long> {
    val used = existing.mapTo(mutableSetOf()) { it.id }
    var candidate = (existing.maxOfOrNull { it.id } ?: 0L) + 1L
    return List(count) {
        while (candidate in used) candidate++
        used.add(candidate)
        candidate++
    }
}

/**
 * 把不连续的勾选合并成区间文案，如 {1,3,4} → "周一 / 周三–周四"。
 *
 * 区间连接符用 **en dash `–`（U+2013）**，与项目既有约定一致
 * （节假日与调休的「第1–3节」、学期与教学周的起止日期都是它），别再用 ASCII `-`
 * 或 em dash `—`。
 */
fun describeSpecialDays(selectedDays: Set<Int>): String {
    if (selectedDays.isEmpty()) return "点选下方星期（不连续可分段保存）"
    return mergeConsecutiveDays(selectedDays.sorted()).joinToString(" / ") { (s, e) ->
        if (s == e) "周${SPECIAL_WEEK_LABELS[s - 1]}"
        else "周${SPECIAL_WEEK_LABELS[s - 1]}–周${SPECIAL_WEEK_LABELS[e - 1]}"
    }
}

/** 单个子块的星期文案，如 "周一–周五" */
fun describeSpecialItemDays(item: SpecialItem): String =
    if (item.startDay == item.endDay) "周${SPECIAL_WEEK_LABELS[item.startDay - 1]}"
    else "周${SPECIAL_WEEK_LABELS[item.startDay - 1]}–周${SPECIAL_WEEK_LABELS[item.endDay - 1]}"

// 已被同横带其他子块占用的星期置灰不可点，避免重叠
@Composable
fun WeekDayRangeSelector(
    selectedDays: Set<Int>,
    occupiedDays: Set<Int>,
    onDayToggle: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (day in 1..7) {
            val selected = day in selectedDays
            val occupied = day in occupiedDays && !selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(ContinuousRoundedRectangle(10.dp))
                    .background(
                        when {
                            selected -> MiuixTheme.colorScheme.primary
                            occupied -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        }
                    )
                    .clickable(enabled = !occupied) { onDayToggle(day) },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = SPECIAL_WEEK_LABELS[day - 1],
                    style = MiuixTheme.textStyles.body2,
                    color = when {
                        selected -> Color.White
                        occupied -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        else -> MiuixTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/**
 * 单个星期安排的编辑弹窗。课表页点横带、设置页点「添加安排」共用同一个，
 * 免得两处的占用校验/合并规则各写一份慢慢走偏。
 */
@Composable
fun SpecialItemEditDialog(
    show: Boolean,
    isEditing: Boolean,
    name: String,
    selectedDays: Set<Int>,
    occupiedDays: Set<Int>,
    liquidGlassBackdrop: Backdrop?,
    onNameChange: (String) -> Unit,
    onDayToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    OverlayDialog(
        title = if (isEditing) "编辑安排" else "添加安排",
        summary = null,
        show = show,
        onDismissRequest = onDismiss,
        liquidGlassBackdrop = liquidGlassBackdrop
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NativeMiuixTextField(
                value = name,
                onValueChange = onNameChange,
                label = "名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                requestFocus = show
            )
            // 按连续区间分组显示：{1,3,4} → "周一 / 周三–周四"
            androidx.compose.material3.Text(
                text = describeSpecialDays(selectedDays),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            WeekDayRangeSelector(
                selectedDays = selectedDays,
                occupiedDays = occupiedDays,
                onDayToggle = onDayToggle
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "保存",
                    onClick = onSave,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
            if (isEditing) {
                TextButton(
                    text = "删除该安排",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDelete()
                    },
                    textColor = SpecialDestructiveColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 安排行尾的圆形图标按钮（编辑 / 删除），尺寸与配色与项目内其它圆形入口一致 */
@Composable
private fun SpecialItemIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MiuixTheme.colorScheme.onBackground,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 特殊课程下的「按星期的安排」内联编辑器，供时间设置页使用。
 *
 * 之前这些子块**只能在课表页点横带**逐格填，设置页看不到也改不了 ——
 * 加完一条「早读」用户根本不知道还有后半截。这里把列表和增删改都搬到设置页，
 * 两处共用 [SpecialItemEditDialog]，规则一致。
 */
@Composable
fun SpecialItemsEditorSection(
    items: List<SpecialItem>,
    onItemsChange: (List<SpecialItem>) -> Unit,
    liquidGlassBackdrop: Backdrop?,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var showDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableLongStateOf(-1L) }
    var editingName by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }

    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(
            text = if (items.isEmpty()) "还没有安排，点这里添加" else "添加安排",
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                editingId = -1L
                editingName = ""
                selectedDays = setOf(1, 2, 3, 4, 5)
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        )
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = item.name.ifBlank { "未命名" },
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    androidx.compose.material3.Text(
                        text = describeSpecialItemDays(item),
                        // 与名称同字号：星期范围是这行真正要读的信息，
                        // 用 footnote2 太小，「周一–周五」在手机上几乎看不清
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
                // 圆形图标按钮而不是「编辑 / 删除」文字按钮：文字按钮在窄屏弹窗里
                // 会把名称那行挤到换行，改成两个等大的圆形按钮后名称有整行宽度
                SpecialItemIconButton(
                    icon = MiuixIcons.Edit,
                    contentDescription = "编辑",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        editingId = item.id
                        editingName = item.name
                        selectedDays = (item.startDay..item.endDay).toSet()
                        showDialog = true
                    }
                )
            }
        }
    }

    val occupiedDays = items
        .filter { it.id != editingId }
        .flatMap { it.startDay..it.endDay }
        .toSet()

    SpecialItemEditDialog(
        show = showDialog,
        isEditing = editingId != -1L,
        name = editingName,
        selectedDays = selectedDays,
        occupiedDays = occupiedDays,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onNameChange = { editingName = it },
        onDayToggle = { day ->
            selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
        },
        onDismiss = { showDialog = false },
        onSave = {
            val trimmed = editingName.trim()
            when {
                trimmed.isEmpty() -> Unit
                selectedDays.isEmpty() -> Unit
                else -> {
                    // 不连续点选合并为连续区间，如 {1,3,4} → 两个子块
                    val ranges = mergeConsecutiveDays(selectedDays.sorted())
                    val base = items.filter { it.id != editingId }
                    val ids = nextSpecialItemIds(base, ranges.size)
                    val newItems = ranges.mapIndexed { i, (s, e) ->
                        SpecialItem(id = ids[i], name = trimmed, startDay = s, endDay = e)
                    }
                    onItemsChange((base + newItems).sortedBy { it.startDay })
                    showDialog = false
                }
            }
        },
        onDelete = {
            onItemsChange(items.filter { it.id != editingId })
            showDialog = false
        }
    )
}