/** 偏好设置页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.haooz.chedule.ui.components.SharedScrollBehavior
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.haooz.chedule.ui.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun PreferenceSettingsScreen(
    scrollBehavior: SharedScrollBehavior? = null,
) {
    var listScrollY by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val eduPrefs = remember { context.getSharedPreferences("edu_import_prefs", Context.MODE_PRIVATE) }
    var repoUrl by remember { mutableStateOf(eduPrefs.getString("repo_url", "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse") ?: "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse") }

    val themePrefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(themePrefs.getString("theme_mode", "system") ?: "system") }

    val settingsViewModel = remember { SettingsViewModel(context.applicationContext as android.app.Application) }
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 16.dp
    val backdropColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropColor)
        drawContent()
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
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .collect { offset ->
                        listScrollY = offset
                    }
            }
            val density = androidx.compose.ui.platform.LocalDensity.current
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
                    top = paddingValues.calculateTopPadding() + topBarHeightDp,
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SmallTitle(
                        text = "外观",
                        modifier = Modifier.offset(x = (-15).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            RadioButtonPreference(
                                title = "跟随系统",
                                selected = themeMode == "system",
                                onClick = {
                                    themeMode = "system"
                                    themePrefs.edit { putString("theme_mode", "system") }
                                }
                            )
                            RadioButtonPreference(
                                title = "浅色模式",
                                selected = themeMode == "light",
                                onClick = {
                                    themeMode = "light"
                                    themePrefs.edit { putString("theme_mode", "light") }
                                }
                            )
                            RadioButtonPreference(
                                title = "深色模式",
                                selected = themeMode == "dark",
                                onClick = {
                                    themeMode = "dark"
                                    themePrefs.edit { putString("theme_mode", "dark") }
                                }
                            )
                        }
                    }
                }
                item {
                    val todayShowWallpaper by settingsViewModel.todayShowWallpaper.collectAsState()

                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            SwitchPreference(
                                title = "今日页显示壁纸",
                                summary = "开启后今日页显示课表页设置的壁纸",
                                checked = todayShowWallpaper,
                                onCheckedChange = { settingsViewModel.setTodayShowWallpaper(it) }
                            )
                        }
                    }
                }

                item {
                    SmallTitle(
                        text = "启动设置",
                        modifier = Modifier.offset(x = (-15).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val homepageEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = "今日",
                                        selected = defaultHomepage == "今日",
                                        onClick = {
                                            settingsViewModel.setDefaultHomepage("今日")
                                        }
                                    ),
                                    DropdownItem(
                                        text = "课程表",
                                        selected = defaultHomepage == "课程表",
                                        onClick = {
                                            settingsViewModel.setDefaultHomepage("课程表")
                                        }
                                    ),
                                )
                            )

                            OverlayDropdownPreference(
                                title = "默认首页",
                                summary = "首次启动时默认显示的页面",
                                entry = homepageEntry,
                                collapseOnSelection = true
                            )
                        }
                    }
                }

                item {
                    SmallTitle(
                        text = "其他",
                        modifier = Modifier.offset(x = (-15).dp)
                    )
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val repoEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = "GitHub",
                                        selected = repoUrl == "https://github.com/XingHeYuZhuan/shiguang_warehouse.git",
                                        onClick = {
                                            repoUrl = "https://github.com/XingHeYuZhuan/shiguang_warehouse.git"
                                            eduPrefs.edit { putString("repo_url", repoUrl) }
                                        }
                                    ),
                                    DropdownItem(
                                        text = "Gitee",
                                        selected = repoUrl == "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse",
                                        onClick = {
                                            repoUrl = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
                                            eduPrefs.edit { putString("repo_url", repoUrl) }
                                        }
                                    ),
                                )
                            )

                            OverlayDropdownPreference(
                                title = "数据仓库源",
                                summary = "更新教务系统数据源的仓库",
                                entry = repoEntry,
                                collapseOnSelection = true
                            )
                        }
                    }
                }
            }
        }
    }
}
