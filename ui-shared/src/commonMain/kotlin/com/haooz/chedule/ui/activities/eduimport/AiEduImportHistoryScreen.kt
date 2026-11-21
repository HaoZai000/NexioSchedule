/** AI 教务导入历史：按时间倒序列出 AI 写过的每一版脚本 */
package com.haooz.chedule.ui.activities.eduimport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.ai.AiEduScriptRecord
import com.haooz.chedule.data.ai.AiEduScriptStore
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.activities.FeedbackPlaceholder
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.SharedScrollBehavior
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.nexioui.window.isTabletWidth
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import androidx.compose.material3.Text as PlainText

@Composable
fun AiEduImportHistoryScreen(
    scrollBehavior: SharedScrollBehavior? = null,
) {
    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 20.dp else 16.dp

    // null = 还没读完（首帧不闪空态）；读完才知道是真没有记录
    var records by remember { mutableStateOf<List<AiEduScriptRecord>?>(null) }
    LaunchedEffect(Unit) {
        records = AiEduScriptStore.load().sortedByDescending { it.createdAtMs }
    }

    Scaffold(topBar = {}) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier,
                ),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = paddingValues.calculateTopPadding() +
                    CollapsibleTopAppBarDefaults.CollapsedHeight +
                    pageTopExtra(isTablet, hasSmallTitle = false),
                bottom = 60.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val list = records
            when {
                list == null -> Unit

                list.isEmpty() -> item(key = "empty") {
                    FeedbackPlaceholder(
                        text = ImportStr.aiEduHistoryEmptyHint(),
                        title = ImportStr.aiEduHistoryEmpty(),
                    )
                }

                else -> items(list, key = { it.id }) { record ->
                    AiEduScriptCard(record)
                }
            }
        }
    }
}

/**
 * 一条脚本记录。
 *
 * 脚本默认折叠：一次分析可能留下五六版，全展开会把列表撑成几屏代码，
 * 真正需要看的时候再展开即可。
 */
@Composable
private fun AiEduScriptCard(record: AiEduScriptRecord) {
    val theme = NexioTheme.colorScheme
    var expanded by remember(record.id) { mutableStateOf(false) }
    val statusColor = if (record.ok) theme.primary else theme.error
    val statusText = if (record.ok) {
        ImportStr.aiEduHistorySuccess(record.courseCount)
    } else {
        ImportStr.aiEduHistoryFailed()
    }

    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(Modifier.size(7.dp))
            Text(
                text = statusText,
                style = NexioTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = statusColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatScriptTime(record.createdAtMs),
                style = NexioTheme.textStyles.footnote2,
                color = theme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = record.note.ifBlank { ImportStr.aiEduHistoryNoNote() },
            style = NexioTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = theme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (record.pageUrl.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = record.pageUrl,
                style = NexioTheme.textStyles.footnote1,
                color = theme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!record.ok && !record.error.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = record.error,
                style = NexioTheme.textStyles.body2,
                color = theme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(ContinuousCapsule())
                .background(theme.surfaceContainer)
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (expanded) {
                    ImportStr.aiEduHistoryHideScript()
                } else {
                    ImportStr.aiEduHistoryViewScript()
                },
                style = NexioTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = theme.primary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ContinuousRoundedRectangle(12.dp))
                    .background(theme.surfaceContainer)
                    .padding(12.dp)
            ) {
                PlainText(
                    text = record.script,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = theme.onSurface,
                )
            }
        }
    }
}

/** epoch ms → 本地「MM-dd HH:mm」。 */
private fun formatScriptTime(ms: Long): String {
    if (ms <= 0L) return ""
    val dt = runCatching {
        Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    }.getOrNull() ?: return ""
    val month = dt.monthNumber.toString().padStart(2, '0')
    val day = dt.dayOfMonth.toString().padStart(2, '0')
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "$month-$day $hour:$minute"
}
