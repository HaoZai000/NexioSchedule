/**
 * 更新设置页 - 更新通道 / 下载源（从更新页右上角菜单进来）
 *
 * 数据仍写在 `update_settings` 这个偏好文件里，跟更新页共用同一份；
 * 切通道要顺手清掉「上次查到的版本」缓存，否则更新页会继续显示旧通道的结果。
 */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.components.text.material.Text as Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun UpdatePreferencesScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.haooz.softglass.backdrop.GlassBackdrop? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val prefs = remember { AppStorage.store("update_settings") }
    var updateChannel by remember { mutableStateOf(prefs.getString("update_channel", "stable")) }
    var downloadSource by remember { mutableStateOf(prefs.getString("download_source", "gitee")) }
    var autoCheckUpdate by remember { mutableStateOf(prefs.getBoolean("auto_check_update", true)) }
    var updateReminder by remember { mutableStateOf(prefs.getBoolean("update_reminder", true)) }
    val effectiveDownloadSource = if (updateChannel == "beta") "gitee" else downloadSource
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    /** 换通道：写盘 + 清掉上次查到的版本缓存（更新页下次进会重查）。 */
    fun setChannel(channel: String) {
        if (updateChannel == channel) return
        updateChannel = channel
        prefs.edit {
            putString("update_channel", channel)
            remove("has_update")
            remove("latest_url")
            remove("latest_apk_url")
            remove("latest_tag")
            remove("latest_name")
            remove("latest_body")
            remove("latest_date")
            remove("last_check_date")
        }
    }

    val isTablet = isTabletWidth()
    val horizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(topBar = {}) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()
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
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = paddingValues.calculateTopPadding() +
                        CollapsibleTopAppBarDefaults.CollapsedHeight +
                        pageTopExtra(isTablet, hasSmallTitle = false),
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val channelEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = AboutStr.stableChannel(),
                                        selected = updateChannel == "stable",
                                        onClick = { setChannel("stable") }
                                    ),
                                    DropdownItem(
                                        text = "Beta",
                                        selected = updateChannel == "beta",
                                        onClick = { setChannel("beta") }
                                    ),
                                )
                            )
                            OverlayDropdownMenu(
                                title = AboutStr.updateChannel(),
                                entry = channelEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                            val downloadSourceEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = "Gitee",
                                        selected = effectiveDownloadSource == "gitee",
                                        onClick = {
                                            downloadSource = "gitee"
                                            prefs.edit { putString("download_source", "gitee") }
                                        }
                                    ),
                                    DropdownItem(
                                        text = "GitHub",
                                        selected = effectiveDownloadSource == "github",
                                        onClick = {
                                            downloadSource = "github"
                                            prefs.edit { putString("download_source", "github") }
                                        }
                                    ),
                                )
                            )
                            OverlayDropdownMenu(
                                title = AboutStr.downloadSource(),
                                summary = if (updateChannel == "beta") {
                                    AboutStr.betaLockedGitee()
                                } else {
                                    AboutStr.downloadSourceSummary()
                                },
                                entry = downloadSourceEntry,
                                enabled = updateChannel != "beta",
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = AboutStr.autoCheckUpdates(),
                                summary = AboutStr.autoCheckUpdatesSummary(),
                                checked = autoCheckUpdate,
                                onCheckedChange = {
                                    autoCheckUpdate = it
                                    prefs.edit { putBoolean("auto_check_update", it) }
                                }
                            )
                            SwitchPreference(
                                title = AboutStr.updateReminder(),
                                summary = AboutStr.updateReminderSummary(),
                                checked = updateReminder,
                                onCheckedChange = {
                                    updateReminder = it
                                    prefs.edit { putBoolean("update_reminder", it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
