package com.haooz.chedule.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 全量消费指针事件。仅用于「自身没有可交互子节点」的全屏遮罩（快照、空占位），
 * 防止点击穿透到下层。
 */
fun Modifier.consumeAllTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        }
    }
}

/**
 * 保持节点可命中以挡住下层兄弟节点，但不 consume，
 * 子节点（LazyColumn / 按钮等）仍可正常滚动和点击。
 * 用于详情页这类「全屏覆盖层 + 内部可交互内容」。
 */
fun Modifier.blockTouchPassThrough(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}
