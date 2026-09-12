package com.haooz.chedule.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 全量消费指针事件，防止点击/滑动穿透到下层。
 * 用于全屏遮罩、快照占位、详情页退出动画层等「视觉覆盖但默认不拦截」的节点。
 */
fun Modifier.consumeAllTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        }
    }
}
