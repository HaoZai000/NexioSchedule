package com.haooz.chedule.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.navigation.AppRoute
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 设置主页：9 个分类卡片，具体设置全部在下一层（除直达型）。
 *
 * 分类分两种（见 [SettingsCategory.directRoute]）：
 * - 容器型（课表 / 个性化 / 数据管理 / 备份与同步 / 更新）→ 进本类的二级列表页；
 * - 直达型（提醒 / 小组件 / 关于 / 捐赠）→ 直接进对应页面，不再套一层「只有一行」的中间页。
 *
 * 「更新」分类仅在提供应用内更新通道的平台出现（iOS / 鸿蒙走应用商店，传 false 隐藏）。
 * 排班模式不走这里 —— 那时主页直接内联「课表」分类内容（见 SettingsScreen）。
 */
@Composable
fun SettingsHome(
    host: SettingsContentHost,
    showUpdateSettings: Boolean,
    scrollBehavior: SharedScrollBehavior? = null,
) {
    val resolver = rememberSettingsAccentResolver()
    // 只有「课表」一行带 summary：当前课表名 + 周次/节次信息，其余分类行保持简洁
    val scheduleName by host.scheduleViewModel.currentScheduleName.collectAsState()
    val scheduleSummaries by host.scheduleViewModel.scheduleSummaries.collectAsState()
    val courseSummary = scheduleSummaries[scheduleName]?.let { "$scheduleName，$it" }
        ?: scheduleName.takeIf { it.isNotBlank() }
    val hapticFeedback = LocalHapticFeedback.current
    val hPad = if (isTabletWidth()) 20.dp else 16.dp
    val listModifier = remember(scrollBehavior) {
        scrollBehavior?.let {
            Modifier
                .collapsibleTopInset(it)
                .nestedScroll(it.nestedScrollConnection)
        } ?: Modifier
    }

    Scaffold(topBar = {}) { paddingValues ->
        // 一个分组一张卡；卡与卡的间距用卡片下边距 12dp 实现，所以 LazyColumn 不再设 spacedBy
        val groups = SettingsCategory.homeGroups(showUpdateSettings = showUpdateSettings)
        LazyColumn(
            state = rememberLazyListState(),
            modifier = listModifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove),
            contentPadding = PaddingValues(
                start = hPad,
                end = hPad,
                // 折叠标题下多留 12dp：主页首张卡没有 SmallTitle 缓冲，贴着顶栏会显得顶死
                top = paddingValues.calculateTopPadding() +
                    CollapsibleTopAppBarDefaults.CollapsedHeight + 12.dp,
                bottom = 60.dp,
            ),
        ) {
            groups.forEachIndexed { index, group ->
                item(key = "g_$index") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            group.forEach { category ->
                                ArrowPreference(
                                    title = category.title(),
                                    // 其余分类不带 summary；课表行显示「课表名，共n周，m节课」
                                    summary = if (category == SettingsCategory.Course) courseSummary else null,
                                    startAction = { SettingsCategoryBadge(category, resolver) },
                                    onClick = {
                                        val direct = category.directRoute
                                        if (direct != null) {
                                            host.onNavigate(direct)
                                        } else {
                                            host.onNavigate(AppRoute.SettingsCategoryDetail(category.key))
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
}
