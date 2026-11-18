package com.haooz.chedule.ui.components.slider

import androidx.compose.ui.input.pointer.PointerEvent

/**
 * 判定一次「触摸被系统或父容器取消」的指针事件。
 *
 * Compose 没有独立的 CANCEL 事件类型，平台会把取消合成为「全部抬起、位置不变且已被消费」。
 * 跨平台层读不到 `MotionEvent`，因此这里只保留这一可判定条件。
 *
 * @return 当前事件是否为取消事件。
 */
internal fun PointerEvent.isNexioTouchCancelled(): Boolean =
    changes.any { it.previousPressed } && changes.all {
        !it.pressed && (!it.previousPressed ||
            (it.isConsumed && it.uptimeMillis == it.previousUptimeMillis &&
                it.position == it.previousPosition))
    }
