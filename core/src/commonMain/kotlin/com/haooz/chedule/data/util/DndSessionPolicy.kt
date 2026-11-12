package com.haooz.chedule.data.util

/** 上课勿扰状态机里不依赖系统服务的判定，单独留出来做表驱动回归。 */
object DndSessionPolicy {

    /** 交还时主通道是否仍等于本应用写入的值；读不到一律不算持有。 */
    fun ownsPrimaryChannel(current: Int?, expected: Int, target: Int): Boolean =
        current != null && (current == expected || current == target)

    /** 次通道是否被用户动过；未观测过一律视为没动过，交还时两条通道都还原。 */
    fun secondaryTouched(applied: Int?, current: Int?): Boolean =
        applied != null && current != null && current != applied

    /** 当前课堂窗口相对今天 00:00 的结束分钟偏移；不在窗口内返回 null。 */
    fun windowEndOffset(startMin: Int, endMin: Int, currentMin: Int): Int? = when {
        startMin == endMin -> null
        startMin < endMin -> if (currentMin in startMin until endMin) endMin else null
        currentMin >= startMin -> endMin + 1440
        currentMin < endMin -> endMin
        else -> null
    }
}
