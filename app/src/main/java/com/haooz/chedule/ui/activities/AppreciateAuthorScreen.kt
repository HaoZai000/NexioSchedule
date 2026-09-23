/** 赞赏作者页面 - Screen（含前三名领奖台排行） */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.R
import com.haooz.chedule.data.AppreciationFetcher
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.basic.collapsibleTopInset
import com.haooz.chedule.ui.data.AppreciationItem
import com.haooz.chedule.ui.utils.isAppDarkTheme
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

private val RankGold = Color(0xFFE8B84B)
private val RankSilver = Color(0xFFA8B0BC)
private val RankBronze = Color(0xFFC48A5A)

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
    // 累计前三（云端聚合，已排除匿名）
    var podium by remember { mutableStateOf<List<AppreciationItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        podium = AppreciationFetcher.fetchTop(limit = 3)
        val page = AppreciationFetcher.fetch(offset = 0)
        donations = page.items
        hasMore = page.hasMore
    }

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
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {

                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemScrollOffset }
                        .collect { offset -> listScrollY = offset }
                }
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
                        .collapsibleTopInset(scrollBehavior)
                        .then(
                            scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                        ),
                    contentPadding = PaddingValues(
                        start = tabletHorizontalPadding,
                        end = tabletHorizontalPadding,
                        top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight + 12.dp,
                        bottom = 60.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        DonateQrCard()
                    }
                    item {
                        PodiumStage(entries = podium)
                    }
                    if (donationList.isNotEmpty()) {
                        item {
                            DonationDetailSection(donationList = donationList)
                        }
                    }
                }

        }
    }
}

@Composable
private fun PodiumStage(entries: List<AppreciationItem>) {
    val isDark = isAppDarkTheme()
    val first = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)

    // 不包外框：舞台直接铺在页面上，只保留榜首光晕
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .then(
                if (first != null) {
                    Modifier.drawSpotlightGlow(isDark)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 固定台座宽度，避免第三名列被 weight 挤压导致文字裁切
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            if (second != null) {
                PodiumColumn(rank = 2, item = second, isDark = isDark)
            } else {
                Spacer(modifier = Modifier.width(104.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (first != null) {
                PodiumColumn(rank = 1, item = first, isDark = isDark)
            } else {
                Spacer(modifier = Modifier.width(120.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (third != null) {
                PodiumColumn(rank = 3, item = third, isDark = isDark)
            } else {
                Spacer(modifier = Modifier.width(104.dp))
            }
        }
    }
}

private fun Modifier.drawSpotlightGlow(isDark: Boolean): Modifier = this.drawBehind {
    val glowColor = if (isDark) {
        Color(0xFFE8B84B).copy(alpha = 0.22f)
    } else {
        Color(0xFFFFE9A8).copy(alpha = 0.55f)
    }
    val radius = size.minDimension * 0.55f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor, Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.28f),
            radius = radius * 1.35f
        ),
        radius = radius * 1.35f,
        center = Offset(size.width / 2f, size.height * 0.28f)
    )
}

@Composable
private fun PodiumColumn(
    rank: Int,
    item: AppreciationItem,
    isDark: Boolean,
) {
    val rankColor = when (rank) {
        1 -> RankGold
        2 -> RankSilver
        else -> RankBronze
    }
    val avatarSize = when (rank) {
        1 -> 72.dp
        2 -> 56.dp
        else -> 54.dp
    }
    val pedestalHeight = when (rank) {
        1 -> 148.dp
        2 -> 120.dp
        else -> 120.dp
    }
    val pedestalWidth = when (rank) {
        1 -> 120.dp
        2 -> 104.dp
        else -> 104.dp
    }
    val ringWidth = when (rank) {
        1 -> 3.dp
        else -> 2.dp
    }
    val nameSize = when (rank) {
        1 -> 14.sp
        else -> 12.sp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(pedestalWidth)
    ) {
        // 头像 + 金属光环
        Box(contentAlignment = Alignment.Center) {
            if (rank == 1) {
                Box(
                    modifier = Modifier
                        .size(avatarSize + 16.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    RankGold.copy(alpha = 0.35f),
                                    RankGold.copy(alpha = 0f),
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    // 手绘柔光代替 shadow：Compose 圆形阴影在深色下会露八边形
                    .drawBehind {
                        val glowAlpha = if (rank == 1) 0.28f else 0.18f
                        drawCircle(
                            color = rankColor.copy(alpha = glowAlpha),
                            radius = this.size.minDimension / 2f + 3.dp.toPx()
                        )
                    }
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(ringWidth, rankColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                DonorAvatar(item = item, size = avatarSize)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 台座（高度可随内容撑开，避免第三名文字被裁切）
        Box(
            modifier = Modifier
                .width(pedestalWidth)
                .heightIn(min = pedestalHeight)
                .clip(ContinuousRoundedRectangle(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = pedestalColors(rankColor, isDark)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.18f else 0.55f),
                            Color.White.copy(alpha = 0.05f),
                        )
                    ),
                    shape = ContinuousRoundedRectangle(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
            ) {
                Text(
                    text = rank.toString(),
                    fontSize = if (rank == 1) 36.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = rankColor.copy(alpha = if (isDark) 0.9f else 0.75f),
                    lineHeight = if (rank == 1) 38.sp else 30.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.nickname,
                    fontSize = nameSize,
                    fontWeight = if (rank == 1) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isDark) Color(0xFFF4F5F7) else Color(0xFF2A2E36),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = nameSize * 1.2f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.amount,
                    fontSize = if (rank == 1) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = rankColor.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun pedestalColors(rankColor: Color, isDark: Boolean): List<Color> {
    return if (isDark) {
        listOf(
            rankColor.copy(alpha = 0.28f),
            rankColor.copy(alpha = 0.16f),
            rankColor.copy(alpha = 0.22f),
        )
    } else {
        listOf(
            rankColor.copy(alpha = 0.42f),
            rankColor.copy(alpha = 0.24f),
            rankColor.copy(alpha = 0.34f),
        )
    }
}

@Composable
private fun DonorAvatar(item: AppreciationItem, size: androidx.compose.ui.unit.Dp) {
    val isAnonymous = item.nickname == "[匿名]" || item.nickname == "匿名"
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (isAnonymous) {
            Icon(
                painter = painterResource(id = R.drawable.ic_anonymous_avatar),
                contentDescription = "匿名",
                modifier = Modifier
                    .size(size)
                    .alpha(0.65f),
                tint = Color.Unspecified
            )
        } else {
            Text(
                text = item.nickname.firstOrNull()?.toString() ?: "?",
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DonateQrCard() {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.zanshangma),
            contentDescription = "赞赏码",
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .clip(ContinuousRoundedRectangle(12.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun DonationDetailSection(donationList: List<AppreciationItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                modifier = Modifier.padding(end = 12.dp)
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

@Composable
private fun AppreciationListItem(item: AppreciationItem) {
    val isAnonymous = item.nickname == "[匿名]" || item.nickname == "匿名"
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
                    modifier = Modifier
                        .size(40.dp)
                        .alpha(0.6f),
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
