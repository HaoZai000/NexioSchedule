package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.haooz.nexioui.navigationback.rememberNavigationBack

/**
 * 把系统返回手势接到一段 0..1 动画进度上（1 = 全开，0 = 收起）。
 *
 * handler 与 backProgress 必须取同一份 state —— 拆成两个 remember 会静默失效。
 * 手势首帧记下当前进度为基准：入场没播完就滑返回时从当前值续接，不跳回全开。
 *
 * @param enabled 是否接管系统返回
 * @param initialProgress 手势首帧的进度基准
 * @param onGestureStart 手势首帧回调，调用方可在此记录自己的其它进度基准
 * @param onProgress 手势期间把 (基准 × 剩余) 写回动画
 * @param onDismissed 完整返回：提交关闭
 * @param onCancelled 手势中途取消：回弹到全开
 */
@Composable
fun PredictiveBackAnimation(
    enabled: Boolean,
    initialProgress: () -> Float,
    onGestureStart: () -> Unit = {},
    onProgress: suspend (Float) -> Unit,
    onDismissed: () -> Unit,
    onCancelled: () -> Unit,
) {
    val back = rememberNavigationBack()
    val currentInitial by rememberUpdatedState(initialProgress)
    val currentGestureStart by rememberUpdatedState(onGestureStart)
    val currentOnProgress by rememberUpdatedState(onProgress)

    back.handler(
        enabled = enabled,
        onBackCompleted = onDismissed,
        onBackCancelled = onCancelled,
    )

    LaunchedEffect(Unit) {
        var sawGesture = false
        var base = 1f
        snapshotFlow { back.backProgress() }.collect { gesture ->
            if (gesture == null) {
                sawGesture = false
                return@collect
            }
            // 0 是松手后的收尾推送，不能 snap 掉进行中的关闭/回弹动画
            if (gesture <= 0f || !PredictiveBackSettings.enabled) return@collect
            if (!sawGesture) {
                sawGesture = true
                base = currentInitial().coerceIn(0f, 1f)
                currentGestureStart()
            }
            currentOnProgress(base * (1f - gesture).coerceIn(0f, 1f))
        }
    }
}
