/** 更新日志页 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.data.changelogData
import com.haooz.chedule.ui.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ChangelogScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    initialExpandCount: Int = 3
) {

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

    val expandedStates = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(changelogData.size) { index ->
                add(index < initialExpandCount)
            }
        }
    }

    val rotations = List(changelogData.size) { index ->
        val rotation by animateFloatAsState(
            targetValue = if (expandedStates[index]) 90f else -90f,
            animationSpec = tween(durationMillis = 200),
            label = "changelogRotation$index"
        )
        rotation
    }

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            val listState = rememberLazyListState()
            val density = LocalDensity.current
            val topBarHeightDp = with(density) {
                (scrollBehavior?.currentHeightPx ?: 0f).toDp()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    end = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + topBarHeightDp + 12.dp,
                    bottom = 60.dp
                )
            ) {
                item {
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            changelogData.forEachIndexed { index, entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedStates[index] = !expandedStates[index] }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                        .padding(
                                            start = 20.dp,
                                            end = 18.dp,
                                            top = if (index == 0) 20.dp else 17.dp,
                                            bottom = if (index == changelogData.lastIndex) 20.dp else 17.dp
                                        ),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.version,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = entry.date,
                                            fontSize = 14.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = MiuixIcons.ChevronForward,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .graphicsLayer { rotationZ = rotations[index] },
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = expandedStates[index],
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
                                        ) {
                                            entry.changes.forEach { change ->
                                                Row(modifier = Modifier.padding(bottom = 2.dp)) {
                                                    Text(
                                                        text = "• ",
                                                        fontSize = 14.sp,
                                                        lineHeight = 22.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                    Text(
                                                        text = change,
                                                        fontSize = 14.sp,
                                                        lineHeight = 22.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 分割线（最后一个不显示）
                                if (index < changelogData.lastIndex) {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp)
                                            .height(0.5.dp)
                                            .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f))
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
