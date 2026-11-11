/** 我的反馈（历史提交 + 节点进度 + 自助删除）- Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.platform.FeedbackClient
import com.haooz.chedule.data.platform.FeedbackRecord
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.FeedbackStr
import com.haooz.chedule.ui.components.FeedbackKindLabel
import com.haooz.chedule.ui.components.FeedbackStageBar
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.chedule.ui.utils.isAppDarkTheme

@Composable
fun FeedbackHistoryScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    onWriteFeedback: () -> Unit = {},
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
) {
    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf<List<FeedbackRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    // 待删除的条目：非空即弹确认框
    var pendingDelete by remember { mutableStateOf<FeedbackRecord?>(null) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        loading = true
        errorText = null
        FeedbackClient.fetchMine().fold(
            onSuccess = {
                records = it
                errorText = null
            },
            onFailure = { errorText = it.message ?: FeedbackStr.loadFailed() },
        )
        loading = false
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
            when {
                loading && records.isEmpty() -> item(key = "loading") {
                    FeedbackPlaceholder(text = FeedbackStr.loading())
                }

                errorText != null && records.isEmpty() -> item(key = "error") {
                    FeedbackPlaceholder(
                        text = errorText.orEmpty(),
                        actionText = FeedbackStr.retry(),
                        onAction = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            reloadKey++
                        },
                    )
                }

                records.isEmpty() -> item(key = "empty") {
                    FeedbackPlaceholder(
                        text = FeedbackStr.historyEmptyBody(),
                        title = FeedbackStr.historyEmpty(),
                        actionText = FeedbackStr.submit(),
                        onAction = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onWriteFeedback()
                        },
                    )
                }

                else -> items(records, key = { it.id }) { record ->
                    FeedbackHistoryCard(
                        record = record,
                        onDelete = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            pendingDelete = record
                        },
                    )
                }
            }
        }
    }

    val target = pendingDelete
    OverlayDialog(
        title = FeedbackStr.deleteTitle(),
        summary = FeedbackStr.deleteBody(),
        show = target != null,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { if (!deleting) pendingDelete = null },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = CommonStr.cancel(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    pendingDelete = null
                },
                modifier = Modifier.weight(1f),
            )
            // 通用删除弹窗：确认键是红字，不是主题色（主题色会让人以为是普通操作）
            TextButton(
                text = FeedbackStr.delete(),
                onClick = {
                    val id = target?.id ?: return@TextButton
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    deleting = true
                    scope.launch {
                        FeedbackClient.deleteMine(id).fold(
                            onSuccess = {
                                records = records.filterNot { it.id == id }
                                pendingDelete = null
                            },
                            onFailure = { showToast(it.message ?: FeedbackStr.deleteFailed()) },
                        )
                        deleting = false
                    }
                },
                textColor = Color(0xFFF44336),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FeedbackHistoryCard(
    record: FeedbackRecord,
    onDelete: () -> Unit,
) {
    val theme = MiuixTheme.colorScheme
    val created = remember(record.createdAt) { formatMoment(record.createdAt) }

    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedbackKindLabel(kind = record.kind)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = created,
                style = MiuixTheme.textStyles.footnote2,
                color = theme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = record.title.ifBlank { FeedbackStr.noTitle() },
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = theme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (record.content.isNotBlank() && record.content != record.title) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = record.content,
                style = MiuixTheme.textStyles.body2,
                color = theme.onSurfaceVariantActions,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 与上方内容（说明 / 标题）之间 14dp
        Spacer(modifier = Modifier.height(14.dp))
        FeedbackStageBar(status = record.status, kind = record.kind)

        val note = record.statusNote
        val releaseVersion = record.version
        if (note.isNotBlank() || (releaseVersion.isNotBlank() && record.status == "released")) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "·",
                    style = MiuixTheme.textStyles.footnote1,
                    color = theme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = buildString {
                        if (note.isNotBlank()) append(note)
                        if (releaseVersion.isNotBlank()) {
                            if (note.isNotBlank()) append("  ")
                            append(FeedbackStr.versionTag(releaseVersion))
                        }
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = theme.onSurfaceVariantActions,
                )
            }
        }

        // 只有「已收到」能删：一旦被采纳/进入流程，删掉会让用户以为进度也丢了
        if (record.status == "received") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                // 与「本地备份」卡片里那颗删除按钮同一套：胶囊底 + 红字，
                // 不是一行灰色小字（那样在卡片里几乎看不见，等于没有入口）
                Box(
                    modifier = Modifier
                        .clip(ContinuousCapsule())
                        .background(
                            if (isAppDarkTheme()) Color(0xFF363636) else Color(0xFFF0F0F0)
                        )
                        .clickable { onDelete() }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = FeedbackStr.delete(),
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF44336),
                    )
                }
            }
        }
    }
}

/** 空态 / 错误 / 加载 三合一：有 actionText 才显示按钮 */
@Composable
internal fun FeedbackPlaceholder(
    text: String,
    title: String = "",
    actionText: String = "",
    onAction: () -> Unit = {},
) {
    val theme = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title2,
                color = theme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = theme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        if (actionText.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** ISO-8601（UTC）→ 设备本地时间；解析不了给 null（调用方自己决定怎么退化） */
private fun parseMoment(iso: String) = runCatching {
    Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
}.getOrNull()

/**
 * ISO-8601（UTC）→ 本地「MM-dd」，**不带时间点**。
 * 开发进度卡右上角只写日期：同一天里连续改几次状态，写成 16:21 / 16:23 只会让人以为条目在刷屏。
 */
internal fun formatDay(iso: String): String {
    if (iso.isBlank()) return ""
    val dt = parseMoment(iso) ?: return iso.take(10)
    return dt.monthNumber.toString().padStart(2, '0') + "-" +
        dt.dayOfMonth.toString().padStart(2, '0')
}

/** ISO-8601（UTC）→ 本地「MM-dd HH:mm」；解析不了就退回原串前 10 位 */
internal fun formatMoment(iso: String): String {
    if (iso.isBlank()) return ""
    val dt = parseMoment(iso) ?: return iso.take(10)
    return formatDay(iso) + " " +
        dt.hour.toString().padStart(2, '0') + ":" +
        dt.minute.toString().padStart(2, '0')
}
