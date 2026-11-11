/** 开发进度（后端维护的 roadmap，每条一张带节点进度条的卡）- Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.platform.FeedbackClient
import com.haooz.chedule.data.platform.FeedbackKind
import com.haooz.chedule.data.platform.RoadmapRecord
import com.haooz.chedule.i18n.FeedbackStr
import com.haooz.chedule.ui.components.FeedbackKindLabel
import com.haooz.chedule.ui.components.FeedbackStageBar
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun FeedbackRoadmapScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    /** 筛选条件由路由持有（控件画在页面固定层） */
    filter: FeedbackKind? = null,
    topExtra: Dp = 0.dp,
) {
    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val hapticFeedback = LocalHapticFeedback.current

    var records by remember { mutableStateOf<List<RoadmapRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        errorText = null
        FeedbackClient.fetchRoadmap().fold(
            onSuccess = {
                records = it
                errorText = null
            },
            onFailure = { errorText = it.message ?: FeedbackStr.loadFailed() },
        )
        loading = false
    }

    // 排序：**待发版钉在最上**（马上就到手，最该被看到）→ 进行中/已排期等（同档按更新时间倒序）
    // → 已发版（倒二，已经交付的沉下去）→ 暂不采纳/重复（最后）。
    // 服务端不再存手工排序值：档位由状态决定，档内由 updated_at 决定。
    val visible = remember(records, filter) {
        val filtered = filter?.let { kind -> records.filter { it.kind == kind } } ?: records
        filtered.sortedWith(
            compareBy<RoadmapRecord> { roadmapRank(it.status) }.thenByDescending { it.updatedAt },
        )
    }

    val listState = rememberLazyListState()
    // 切筛选后回到列表顶部。用程序化 scrollToItem（不经过嵌套滚动），
    // 所以大标题的折叠状态不会被这次跳转改回来 —— 收着就继续收着。
    LaunchedEffect(filter) {
        listState.scrollToItem(0)
    }

    Scaffold(topBar = {}) { paddingValues ->
        LazyColumn(
            state = listState,
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
                top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                    pageTopExtra(isTablet, hasSmallTitle = false) + topExtra,
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

                visible.isEmpty() -> item(key = "empty") {
                    if (records.isEmpty()) {
                        FeedbackPlaceholder(
                            title = FeedbackStr.roadmapEmpty(),
                            text = FeedbackStr.roadmapEmptyBody(),
                        )
                    } else {
                        FeedbackPlaceholder(text = FeedbackStr.filterEmpty())
                    }
                }

                else -> items(visible, key = { it.id }) { record ->
                    RoadmapCard(record)
                }
            }
        }
    }
}

/** 0 = 还没发出去，1 = 已发，2 = 已关闭 */
private fun roadmapRank(status: String): Int = when (status) {
    // 0 待发版：已经做好了、只等版本，最该被看到
    "pending_release" -> 0
    // 2 已发版：已经交付，沉到倒数第二
    "released" -> 2
    // 3 暂不采纳 / 重复提交：最末
    "rejected", "duplicate" -> 3
    // 1 其余（已收到 / 评估中 / 已排期 / 开发中 / 开发完成）
    else -> 1
}

@Composable
private fun RoadmapCard(record: RoadmapRecord) {
    val theme = MiuixTheme.colorScheme

    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedbackKindLabel(kind = record.kind)
            Spacer(modifier = Modifier.weight(1f))
            // 右上角：版本号 · 日期（只到天，不写时间点）
            val updatedDay = remember(record.updatedAt) { formatDay(record.updatedAt) }
            val metaText = remember(record.version, updatedDay) {
                buildString {
                    if (record.version.isNotBlank()) {
                        append(FeedbackStr.versionTag(record.version))
                    }
                    if (updatedDay.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(updatedDay)
                    }
                }
            }
            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    style = MiuixTheme.textStyles.body2,
                    color = theme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = record.title,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = theme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (record.summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = record.summary,
                style = MiuixTheme.textStyles.body2,
                color = theme.onSurfaceVariantActions,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 与上方内容（说明 / 标题）之间 14dp
        Spacer(modifier = Modifier.height(14.dp))
        FeedbackStageBar(status = record.status, kind = record.kind)
    }
}
