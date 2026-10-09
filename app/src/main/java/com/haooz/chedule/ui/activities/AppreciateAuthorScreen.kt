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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
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

/** 名次金属色： */
private fun rankAccent(rank: Int, isDark: Boolean): Color = when (rank) {
    1 -> Color(0xFFE8C26A)
    2 ->  Color(0xFFC5CDD8)
    else ->  Color(0xFFD4A574)
}

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
            // 单列滚动：赞赏码在上、领奖台与捐赠明细在下（pad 也不做页内左右分栏）
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
                    top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                        (if (isTablet) 24.dp else 12.dp),
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DonateQrCard(isTablet = isTablet)
                }
                item {
                    PodiumStage(entries = podium, isTablet = isTablet)
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
private fun PodiumStage(entries: List<AppreciationItem>, isTablet: Boolean = false) {
    val isDark = isAppDarkTheme()
    val first = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)

    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(
            text = "累计赞助",
        )
        // 与赞赏码/捐赠明细同卡容器：宽度与兄弟区块一致，小屏不再被固定台座挤爆
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isTablet) 300.dp else 260.dp)
                    .then(if (first != null) Modifier.drawWinnerAura(isDark) else Modifier),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PodiumColumn(
                        rank = 2,
                        item = second,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    PodiumColumn(
                        rank = 1,
                        item = first,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    PodiumColumn(
                        rank = 3,
                        item = third,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 第一名背后极淡的暖金氛围光，浅色也克制，不再糊成一团黄斑 */
private fun Modifier.drawWinnerAura(isDark: Boolean): Modifier = this.drawBehind {
    val glow = Color(0xFFE8C26A).copy(alpha = 0.14f)
    val radius = size.minDimension * 0.5f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow, Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.34f),
            radius = radius * 1.35f
        ),
        radius = radius * 1.35f,
        center = Offset(size.width / 2f, size.height * 0.34f)
    )
}

@Composable
private fun PodiumColumn(
    rank: Int,
    item: AppreciationItem?,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = rankAccent(rank, isDark)
    val avatarSize = when (rank) {
        1 -> 68.dp
        2 -> 52.dp
        else -> 50.dp
    }
    val pedestalHeight = when (rank) {
        1 -> 104.dp
        2 -> 84.dp
        else -> 76.dp
    }
    val nameSize = when (rank) {
        1 -> 13.sp
        else -> 12.sp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // 头像 + 金属细环（不再大面积染色）
        Box(contentAlignment = Alignment.Center) {
            if (rank == 1) {
                Box(
                    modifier = Modifier
                        .size(avatarSize + 18.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.22f),
                                    accent.copy(alpha = 0f),
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .drawBehind {
                        drawCircle(
                            color = accent.copy(alpha = if (rank == 1) 0.20f else 0.10f),
                            radius = this.size.minDimension / 2f + 2.5.dp.toPx()
                        )
                    }
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = if (rank == 1) 2.5.dp else 1.5.dp,
                        color = accent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item != null) {
                    DonorAvatar(item = item, size = avatarSize)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 台座：中性表面 + 金属细节点缀（避免大块金银铜染色显得廉价）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = pedestalHeight)
                .clip(ContinuousRoundedRectangle(14.dp))
                .background(Brush.verticalGradient(pedestalColors(isDark)))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = if (isDark) 0.36f else 0.28f),
                            accent.copy(alpha = if (isDark) 0.10f else 0.08f),
                        )
                    ),
                    shape = ContinuousRoundedRectangle(14.dp)
                )
                .drawBehind {
                    // 顶部金属高光线
                    val h = 2.dp.toPx()
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0f),
                                accent.copy(alpha = if (isDark) 0.55f else 0.45f),
                                accent.copy(alpha = 0f),
                            )
                        ),
                        topLeft = Offset(0f, 0f),
                        size = size.copy(height = h),
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Text(
                    text = rank.toString(),
                    fontSize = if (rank == 1) 30.sp else 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    lineHeight = if (rank == 1) 32.sp else 26.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item?.nickname ?: "—",
                    fontSize = nameSize,
                    fontWeight = if (rank == 1) FontWeight.SemiBold else FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface.copy(
                        alpha = if (item == null) 0.35f else 1f
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = nameSize * 1.25f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item?.amount.orEmpty(),
                    fontSize = if (rank == 1) 14.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 三列统一中性台座底色，名次差异交给金属描边/数字 */
private fun pedestalColors(isDark: Boolean): List<Color> {
    return if (isDark) {
        listOf(
            Color.White.copy(alpha = 0.07f),
            Color.White.copy(alpha = 0.035f),
        )
    } else {
        listOf(
            Color(0xFFF3F4F6),
            Color(0xFFE8EAEE),
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
private fun DonateQrCard(isTablet: Boolean = false) {
    // 平板端赞赏码卡片居中并占 0.8 宽，避免拉得过扁
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isTablet) 0.8f else 1f)
                .aspectRatio(1f),
            insideMargin = PaddingValues(0.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.zanshangma),
                contentDescription = "赞赏码",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .clip(ContinuousRoundedRectangle(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }
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
