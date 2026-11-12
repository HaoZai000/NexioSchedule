/** 捐赠支持页面 - Screen（感谢卡片 + 累计赞助前三 + 最近支持） */
package com.haooz.chedule.ui.activities

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.platform.AppreciationFetcher
import com.haooz.chedule.data.platform.AppreciationItem
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_anonymous_avatar
import com.haooz.chedule.uishared.resources.zanshangma
import com.haooz.softglass.shape.ContinuousCapsule
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 捐赠页主色：暖金，只用于名次与点缀，不铺大面积背景 */
private val WarmGold = Color(0xFFE8C26A)

/** 名次金属色（浅色主题下压暗，保证在浅底上的可读性） */
private fun rankAccent(rank: Int, isDark: Boolean): Color = when (rank) {
    1 -> if (isDark) Color(0xFFE8C26A) else Color(0xFFB07C11)
    2 -> if (isDark) Color(0xFFC5CDD8) else Color(0xFF6C7683)
    else -> if (isDark) Color(0xFFD4A574) else Color(0xFFA46A32)
}

/** 捐赠名单的加载状态：区分「加载中 / 成功 / 失败」，失败时给重试入口 */
private enum class DonationLoadState { Loading, Ready, Failed }

@Composable
fun AppreciateAuthorScreen(
    scrollBehavior: SharedScrollBehavior? = null,
) {
    // 捐赠明细：云端分页拉取，默认 10 条，滚动到底自动追加下 10 条
    var donations by remember { mutableStateOf<List<AppreciationItem>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreFailed by remember { mutableStateOf(false) }
    var loadState by remember { mutableStateOf(DonationLoadState.Loading) }
    // 累计前三（云端聚合，已排除匿名）
    var podium by remember { mutableStateOf<List<AppreciationItem>>(emptyList()) }

    val scope = rememberCoroutineScope()

    suspend fun loadFirstPage() {
        loadState = DonationLoadState.Loading
        loadMoreFailed = false
        val page = AppreciationFetcher.fetch(offset = 0)
        donations = page.items
        hasMore = page.hasMore
        loadState = if (page.failed) DonationLoadState.Failed else DonationLoadState.Ready
    }

    suspend fun loadMore() {
        if (loadingMore || !hasMore || loadMoreFailed) return
        loadingMore = true
        val page = AppreciationFetcher.fetch(offset = donations.size)
        if (page.failed) {
            loadMoreFailed = true
        } else {
            donations = donations + page.items
            hasMore = page.hasMore
        }
        loadingMore = false
    }

    LaunchedEffect(Unit) {
        podium = AppreciationFetcher.fetchTop(limit = 3)
        loadFirstPage()
    }

    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    // 平板端整页内容收在固定宽度内居中，避免卡片被拉得过宽
    val contentMaxWidth = if (isTablet) 640.dp else null

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                snapshotFlow { listState.canScrollForward to donations.size }
                    .collect { (canScroll, _) ->
                        if (donations.isNotEmpty() && !canScroll) loadMore()
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
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                        pageTopExtra(isTablet, hasSmallTitle = false),
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CenteredItem(maxWidth = contentMaxWidth) {
                        SupportHero(isTablet = isTablet)
                    }
                }
                if (podium.isNotEmpty()) {
                    item {
                        CenteredItem(maxWidth = contentMaxWidth) {
                            SponsorBoard(entries = podium, isTablet = isTablet)
                        }
                    }
                }
                item {
                    CenteredItem(maxWidth = contentMaxWidth) {
                        RecentSupportSection(
                            donations = donations,
                            loadState = loadState,
                            onRetry = { scope.launch { loadFirstPage() } },
                        )
                    }
                }
                if (loadingMore || loadMoreFailed) {
                    item {
                        CenteredItem(maxWidth = contentMaxWidth) {
                            LoadMoreFooter(
                                loading = loadingMore,
                                failed = loadMoreFailed,
                                onRetry = {
                                    scope.launch {
                                        loadMoreFailed = false
                                        loadMore()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 平板端把内容收进 [maxWidth] 并水平居中；手机端占满 */
@Composable
private fun CenteredItem(maxWidth: Dp?, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = if (maxWidth != null) {
                Modifier.widthIn(max = maxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallTitle(text = title)
        Spacer(modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) {
            Text(
                text = trailing,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}

// ── 顶部感谢卡片：文案 + 赞赏码 ──

@Composable
private fun SupportHero(isTablet: Boolean) {
    val isDark = isAppDarkTheme()
    val qrSize = if (isTablet) 236.dp else 228.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // 暖金氛围光藏在二维码后方，克制到不抢文字
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                WarmGold.copy(alpha = if (isDark) 0.10f else 0.15f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.62f),
                            radius = size.maxDimension * 0.62f,
                        )
                    )
                }
        ) {
            if (isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroCopy(
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                    )
                    DonationQrBlock(size = qrSize)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HeroCopy(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    DonationQrBlock(size = qrSize)
                }
            }
        }
    }
}

@Composable
private fun HeroCopy(textAlign: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = AboutStr.donateHeroTitle(),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = AboutStr.donateHeroSubtitle(),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DonationQrBlock(size: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(ContinuousRoundedRectangle(18.dp))
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = Color.Black.copy(alpha = 0.06f),
                    shape = ContinuousRoundedRectangle(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.zanshangma),
                contentDescription = AboutStr.donateQr(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(ContinuousRoundedRectangle(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = AboutStr.donateScanAction(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = AboutStr.donateScanHint(),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 250.dp),
        )
    }
}

// ── 累计赞助前三 ──

@Composable
private fun SponsorBoard(entries: List<AppreciationItem>, isTablet: Boolean) {
    val isDark = isAppDarkTheme()
    val first = entries.getOrNull(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = AboutStr.totalSponsorship(),
            trailing = "",
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (first != null) Modifier.drawWinnerAura(isDark) else Modifier),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PodiumSlot(
                        rank = 2,
                        item = entries.getOrNull(1),
                        isDark = isDark,
                        isTablet = isTablet,
                        modifier = Modifier.weight(1f)
                    )
                    PodiumSlot(
                        rank = 1,
                        item = first,
                        isDark = isDark,
                        isTablet = isTablet,
                        modifier = Modifier.weight(1f)
                    )
                    PodiumSlot(
                        rank = 3,
                        item = entries.getOrNull(2),
                        isDark = isDark,
                        isTablet = isTablet,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 第一名背后极淡的暖金氛围光 */
private fun Modifier.drawWinnerAura(isDark: Boolean): Modifier = this.drawBehind {
    if (isDark) {
        val glow = WarmGold.copy(alpha = 0.12f)
        val center = Offset(size.width / 2f, size.height * 0.3f)
        val radius = size.minDimension * 1.35f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    } else {
        // 浅色模式：整卡一层很淡的暖金竖向渐变，亮而不压底色
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    WarmGold.copy(alpha = 0.13f),
                    WarmGold.copy(alpha = 0.05f),
                    WarmGold.copy(alpha = 0f),
                ),
                startY = 0f,
                endY = size.height,
            )
        )
    }
}

@Composable
private fun PodiumSlot(
    rank: Int,
    item: AppreciationItem?,
    isDark: Boolean,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = rankAccent(rank, isDark)
    val champion = rank == 1
    val shape = ContinuousRoundedRectangle(16.dp)
    val avatarSize = when {
        champion && isTablet -> 68.dp
        champion -> 60.dp
        isTablet -> 56.dp
        else -> 48.dp
    }
    val footerHeight = when (rank) {
        1 -> 52.dp
        2 -> 40.dp
        else -> 34.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(podiumColumnColors(rank, isDark)))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = if (champion) 0.34f else 0.20f),
                shape = shape,
            )
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // 头像 + 名次金属环
        Box(contentAlignment = Alignment.Center) {
            if (champion) {
                Box(
                    modifier = Modifier
                        .size(avatarSize + 18.dp)
                        .clip(ContinuousCapsule())
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.18f),
                                    accent.copy(alpha = 0f),
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(ContinuousCapsule())
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = if (champion) 2.dp else 1.5.dp,
                        color = accent,
                        shape = ContinuousCapsule()
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item != null) {
                    DonorAvatar(item = item, size = avatarSize - 4.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item?.let { donorName(it.nickname) } ?: AboutStr.supportOpenSlot(),
            fontSize = if (champion) 13.5.sp else 12.5.sp,
            fontWeight = if (champion) FontWeight.SemiBold else FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface.copy(
                alpha = if (item == null) 0.35f else 1f
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = if (champion) 18.sp else 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item?.amount.orEmpty(),
            fontSize = if (champion) 12.5.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 台座面：一整块浅金属面，名次数字落在底部，三列高度差形成台阶
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(footerHeight)
                .background(accent.copy(alpha = if (isDark) 0.10f else 0.07f))
                .drawBehind {
                    drawLine(
                        color = accent.copy(alpha = if (isDark) 0.22f else 0.16f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                fontSize = when (rank) {
                    1 -> 26.sp
                    2 -> 21.sp
                    else -> 19.sp
                },
                fontWeight = FontWeight.Medium,
                color = accent.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 三列各自的底色：冠军暖金，其余中性；金属色只做极淡铺底 */
private fun podiumColumnColors(rank: Int, isDark: Boolean): List<Color> {
    val accent = rankAccent(rank, isDark)
    return if (isDark) {
        listOf(
            accent.copy(alpha = if (rank == 1) 0.13f else 0.07f),
            Color.White.copy(alpha = 0.035f),
        )
    } else {
        listOf(
            accent.copy(alpha = if (rank == 1) 0.16f else 0.07f),
            Color(0xFFF2F3F5),
        )
    }
}

// ── 最近支持 ──

@Composable
private fun RecentSupportSection(
    donations: List<AppreciationItem>,
    loadState: DonationLoadState,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = AboutStr.recentSupport(),
            trailing = AboutStr.supportUpdated(),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp)
        ) {
            when {
                loadState == DonationLoadState.Loading -> DonationPlaceholder(
                    text = CommonStr.loading(),
                    showProgress = true,
                )
                loadState == DonationLoadState.Failed -> DonationPlaceholder(
                    text = AboutStr.donationLoadFailed(),
                    onRetry = onRetry,
                )
                donations.isEmpty() -> DonationPlaceholder(
                    text = AboutStr.donationEmpty(),
                )
                else -> Column(modifier = Modifier.fillMaxWidth()) {
                    donations.forEachIndexed { index, item ->
                        SupporterRow(item = item)
                        if (index < donations.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 68.dp, end = 16.dp)
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

@Composable
private fun SupporterRow(item: AppreciationItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(ContinuousCapsule())
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            if (isAnonymousNickname(item.nickname)) {
                Icon(
                    painter = painterResource(Res.drawable.ic_anonymous_avatar),
                    contentDescription = AboutStr.anonymous(),
                    modifier = Modifier
                        .size(40.dp)
                        .alpha(0.6f),
                    tint = Color.Unspecified
                )
            } else {
                Text(
                    text = donorInitial(item.nickname),
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
                text = donorName(item.nickname),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.remark.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.remark,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier.padding(start = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = item.amount,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
            if (item.time.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.time,
                    fontSize = 11.5.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** 名单占位：加载中 / 失败重试 / 暂无记录 */
@Composable
private fun DonationPlaceholder(
    text: String,
    showProgress: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = text,
            fontSize = 12.5.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = CommonStr.retry(),
                onClick = onRetry,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@Composable
private fun LoadMoreFooter(loading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading && !failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = CommonStr.loading(),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        } else {
            TextButton(
                text = CommonStr.retry(),
                onClick = onRetry,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

// ── 捐赠人展示辅助 ──

/** 后端把匿名记成 `[匿名]`，展示时去掉方括号 */
private fun donorName(raw: String): String {
    val cleaned = raw.trim().trim('[', ']').trim()
    return cleaned.ifEmpty { AboutStr.anonymous() }
}

@Composable
private fun isAnonymousNickname(raw: String): Boolean {
    val cleaned = raw.trim().trim('[', ']').trim()
    return cleaned.isEmpty() || cleaned == "匿名" || cleaned == AboutStr.anonymous()
}

private fun donorInitial(raw: String): String {
    val cleaned = raw.trim().trim('[', ']').trim()
    return cleaned.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

@Composable
private fun DonorAvatar(item: AppreciationItem, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(ContinuousCapsule())
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (isAnonymousNickname(item.nickname)) {
            Icon(
                painter = painterResource(Res.drawable.ic_anonymous_avatar),
                contentDescription = AboutStr.anonymous(),
                modifier = Modifier
                    .size(size)
                    .alpha(0.65f),
                tint = Color.Unspecified
            )
        } else {
            Text(
                text = donorInitial(item.nickname),
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
}
