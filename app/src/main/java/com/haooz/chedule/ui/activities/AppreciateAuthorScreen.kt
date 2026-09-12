/** 赞赏作者页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.R
import com.haooz.chedule.data.AppreciationFetcher
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.data.AppreciationItem
import com.haooz.chedule.ui.utils.overScrollVertical
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun AppreciateAuthorScreen(
    scrollBehavior: SharedScrollBehavior? = null,
) {
    var listScrollY by remember { mutableIntStateOf(0) }

    // 捐赠明细：云端分页拉取，默认 10 条，滚动到底自动追加下 10 条
    var donations by remember { mutableStateOf<List<AppreciationItem>>(emptyList()) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    // 首屏加载第一页
    LaunchedEffect(Unit) {
        val page = AppreciationFetcher.fetch(offset = 0)
        donations = page.items
        hasMore = page.hasMore
    }

    // 滚动到底时拉取下一页（带防抖，避免并发重复请求）
    suspend fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        val page = AppreciationFetcher.fetch(offset = donations.size)
        donations = donations + page.items
        hasMore = page.hasMore
        loadingMore = false
    }

    val donationList = donations

    val backdropColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropColor)
        drawContent()
    }
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val topBarHeightDp = with(density) {
                (scrollBehavior?.currentHeightPx ?: 0f).toDp()
            }
            if (isTablet) {
                // 平板：左侧固定图片 + 右侧独立滚动列表
                val tabletListState = rememberLazyListState()
                LaunchedEffect(tabletListState) {
                    // 触发条件：已滚到底(无法继续前滚)或内容不足一屏。含 donations.size 使加载后重新评估，
                    // 从而能继续加载末尾不足 10 条的剩余分页
                    snapshotFlow { tabletListState.canScrollForward to donations.size }
                        .collect { (canScroll, _) ->
                            if (donations.isNotEmpty() && !canScroll && hasMore) loadMore()
                        }
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = tabletHorizontalPadding,
                            end = tabletHorizontalPadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 左侧 - 赞赏码（固定，不可滚动）
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                top = paddingValues.calculateTopPadding() + topBarHeightDp + 12.dp,
                                bottom = 60.dp
                            )
                            .aspectRatio(1f),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.zanshangma),
                            contentDescription = "赞赏码",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .clip(ContinuousRoundedRectangle(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    // 右侧 - 捐赠明细（独立滚动）
                    if (donationList.isNotEmpty()) {
                        LazyColumn(
                            state = tabletListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .overScrollVertical()
                                .scrollEndHaptic(
                                    hapticFeedbackType = androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                )
                                .then(
                                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                                ),
                            contentPadding = PaddingValues(
                                top = paddingValues.calculateTopPadding() + topBarHeightDp + 12.dp,
                                bottom = 60.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SmallTitle(
                                        text = "捐赠明细",
                                        modifier = Modifier.offset(x = (-16).dp)
                                    )
                                    Text(
                                        text = "正在手工填写中",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                    )
                                }
                            }
                            item {
                                Card(
                                    cornerRadius = 20.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                    insideMargin = PaddingValues(0.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        donationList.forEachIndexed { index, item ->
                                            AppreciationListItem(item = item)
                                            if (index < donationList.lastIndex) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .height(0.5.dp)
                                                        .background(MiuixTheme.colorScheme.surfaceVariant)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 手机：上下排列，整体滚动
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemScrollOffset }
                        .collect { offset ->
                            listScrollY = offset
                        }
                }
                // 滚动到底自动加载下一页捐赠
                LaunchedEffect(listState) {
                    snapshotFlow { listState.canScrollForward to donations.size }
                        .collect { (canScroll, _) ->
                            if (donations.isNotEmpty() && !canScroll && hasMore) loadMore()
                        }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                        )
                        .then(
                            scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                        ),
                    contentPadding = PaddingValues(
                        start = tabletHorizontalPadding,
                        end = tabletHorizontalPadding,
                        top = paddingValues.calculateTopPadding() + topBarHeightDp + 12.dp,
                        bottom = 60.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            cornerRadius = 20.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.zanshangma),
                                contentDescription = "赞赏码",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                                    .clip(ContinuousRoundedRectangle(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }

                    if (donationList.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SmallTitle(
                                        text = "捐赠明细",
                                        modifier = Modifier.offset(x = (-16).dp)
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "正在手工填写中",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                    )
                            }
                            Card(
                                cornerRadius = 20.dp,
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    donationList.forEachIndexed { index, item ->
                                        AppreciationListItem(item = item)
                                        if (index < donationList.lastIndex) {
                                            Spacer(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp)
                                                    .height(0.5.dp)
                                                    .background(MiuixTheme.colorScheme.surfaceVariant)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppreciationListItem(item: AppreciationItem) {
    val isAnonymous = item.nickname == "[匿名]"
    val displayName = item.nickname

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (isAnonymous) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_anonymous_avatar),
                    contentDescription = "匿名",
                    modifier = Modifier.size(40.dp).alpha(0.6f),
                    tint = Color.Unspecified
                )
            } else {
                Text(
                    text = item.nickname.firstOrNull()?.toString() ?: "?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = displayName,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            if (item.remark.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.remark,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.time,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )

        }

        Text(
            text = item.amount,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary
        )
    }
}
