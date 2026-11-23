package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.progressindicator.CircularProgressIndicator
import com.haooz.nexioui.theme.NexioTheme
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

/** 触底加载指示器的最短展示时长，避免加载过快时圆环一闪而过 */
internal const val LoadMoreMinVisibleMillis = 240L

/** 记录加载起点，之后用它补齐 [LoadMoreMinVisibleMillis] 的最短展示 */
internal fun markLoadMoreStart(): TimeMark = TimeSource.Monotonic.markNow()

/** 数据已落到列表后调用：圆环补足最短展示时长再收起 */
internal suspend fun awaitLoadMoreMinVisible(startedAt: TimeMark) {
    val remaining = LoadMoreMinVisibleMillis - startedAt.elapsedNow().inWholeMilliseconds
    if (remaining > 0) delay(remaining)
}

/** 列表触底加载页脚：加载中转圆环，失败时给重试按钮 */
@Composable
internal fun LoadMoreFooter(
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading && !failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    size = 24.dp,
                    strokeWidth = 4.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = CommonStr.loading(),
                    fontSize = 15.sp,
                    color = NexioTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        } else {
            FlatTextButton(
                text = CommonStr.retry(),
                onClick = onRetry,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}
