/** 交流与反馈页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.ui.utils.screenWidthDp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun CommunicationScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    /** 应用内反馈：手机端进路由栈，平板端在右栏切 dest（所以用回调而不是直接拿 router） */
    onOpenFeedback: () -> Unit = {},
    /** 开发进度 */
    onOpenRoadmap: () -> Unit = {},
) {
    val isTablet = screenWidthDp() >= 600
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val listState = rememberLazyListState()
            val hapticFeedback = LocalHapticFeedback.current
            val uriHandler = LocalUriHandler.current
            val communicationGroupUrl =
                "https://qun.qq.com/universal-share/share?ac=1&authKey=ovypS7JPJ4dfnh%2FKgN0jtJk%2BdsVhcQvjZEt67oApCjmwUooXefmJUW%2BP1B50bd%2B%2F&busi_data=eyJncm91cENvZGUiOiIxMDAxNTUxNzQxIiwidG9rZW4iOiJWbHd2N1VvMXVObzhpcUtiRUdVZVVwVksxcE5pR0NjMTdiTW45emdONk10dEFZQ2dqc25uMGRkQ2ttMG5NdWx0IiwidWluIjoiNDM5MDg5NzAzIn0%3D&data=6fyq_pHS0i9AI0qTMHv9Xh2HWoCh1RTT12wL1vk29GnjXUR_B1MYN7rISppz9HIZ1nOaDhF-QBkU9Wc4Vypstw&svctype=4&tempid=h5_group_info"
            val feedbackUrl =
                "https://github.com/HaoZai000/NexioSchedule/issues"
            val qqGroupUrl =
                "https://pd.qq.com/s/g4n2qm2sx?b=9"
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                    .collapsibleTopInset(scrollBehavior)
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                            ?: Modifier
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    end = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight,
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SmallTitle(
                        text = AboutStr.contactUs(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArrowPreference(
                                title = AboutStr.joinGroup(),
                                summary = AboutStr.joinGroupSummary(),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    uriHandler.openUri(communicationGroupUrl)
                                }
                            )
                            ArrowPreference(
                                title = AboutStr.joinChannel(),
                                summary = AboutStr.joinChannelSummary(),
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
                        text = AboutStr.feedbackSuggestions(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArrowPreference(
                                title = AboutStr.feedbackOnGitHub(),
                                summary = AboutStr.githubIssuesSummary(),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    uriHandler.openUri(feedbackUrl)
                                }
                            )
                            ArrowPreference(
                                title = AboutStr.feedbackForm(),
                                summary = AboutStr.feedbackFormSummary(),
                                onClick = {
                                    onOpenFeedback()
                                }
                            )
                        }
                    }
                }
                item {
                    SmallTitle(
                        text = AboutStr.featureProgress(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArrowPreference(
                                title = AboutStr.featureProgressTitle(),
                                summary = AboutStr.featureProgressSummary(),
                                onClick = {
                                    onOpenRoadmap()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
