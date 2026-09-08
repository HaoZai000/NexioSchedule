/** 交流与反馈页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun CommunicationScreen(
    scrollBehavior: SharedScrollBehavior? = null,
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
                    .collect { }
            }
            val hapticFeedback = LocalHapticFeedback.current
            val uriHandler = LocalUriHandler.current
            val communicationGroupUrl =
                "https://qun.qq.com/universal-share/share?ac=1&authKey=ovypS7JPJ4dfnh%2FKgN0jtJk%2BdsVhcQvjZEt67oApCjmwUooXefmJUW%2BP1B50bd%2B%2F&busi_data=eyJncm91cENvZGUiOiIxMDAxNTUxNzQxIiwidG9rZW4iOiJWbHd2N1VvMXVObzhpcUtiRUdVZVVwVksxcE5pR0NjMTdiTW45emdONk10dEFZQ2dqc25uMGRkQ2ttMG5NdWx0IiwidWluIjoiNDM5MDg5NzAzIn0%3D&data=6fyq_pHS0i9AI0qTMHv9Xh2HWoCh1RTT12wL1vk29GnjXUR_B1MYN7rISppz9HIZ1nOaDhF-QBkU9Wc4Vypstw&svctype=4&tempid=h5_group_info"
            val feedbackUrl =
                "https://github.com/HaoZai000/NexioSchedule/issues"
            val qqGroupUrl =
                "https://pd.qq.com/s/g4n2qm2sx?b=9"
            val tableUrl =
                "https://docs.qq.com/sheet/DSUV2dWxLa09XQXRZ?tab=BB08J2&scode="
            val density = LocalDensity.current
            val topBarHeightDp = with(density) {
                (scrollBehavior?.currentHeightPx ?: 0f).toDp()
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                            ?: Modifier
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    end = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + topBarHeightDp,
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SmallTitle(
                        text = "联系我们",
                        modifier = Modifier.offset(x = (-16).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArrowPreference(
                                title = "加入交流群",
                                summary = "加入QQ群与其他用户交流",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    uriHandler.openUri(communicationGroupUrl)
                                }
                            )
                            ArrowPreference(
                                title = "加入频道",
                                summary = "加入QQ频道与其他用户交流",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    uriHandler.openUri(qqGroupUrl)
                                }
                            )
                        }
                    }
                }
                item {
                    SmallTitle(
                        text = "反馈与建议",
                        modifier = Modifier.offset(x = (-16).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArrowPreference(
                                title = "前往GitHub反馈",
                                summary = "在GitHub Issues提交反馈和建议",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    uriHandler.openUri(feedbackUrl)
                                }
                            )
                            ArrowPreference(
                                title = "填写表单反馈",
                                summary = "在表单中提交反馈和建议",
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    uriHandler.openUri(tableUrl)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}