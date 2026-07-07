/** 偏好设置页面 */
package com.haooz.chedule.ui.activities

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor

class PreferenceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
                PreferenceSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun PreferenceSettingsScreen(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    var listScrollY by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val eduPrefs = remember { context.getSharedPreferences("edu_import_prefs", Context.MODE_PRIVATE) }
    var repoUrl by remember { mutableStateOf(eduPrefs.getString("repo_url", "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse") ?: "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse") }

    val themePrefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(themePrefs.getString("theme_mode", "system") ?: "system") }

    val viewModel = remember { CourseViewModel(context.applicationContext as android.app.Application) }
    val settingsViewModel = remember { SettingsViewModel(context.applicationContext as android.app.Application) }
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()
    val navBarStyle by settingsViewModel.navBarStyle.collectAsState()

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val blurAlpha = if (listScrollY < 50) 0f else ((listScrollY - 50) / 30f).coerceIn(0f, 0.7f)
    val topBarColorProgress = ((listScrollY - 50) / 30f).coerceIn(0f, 1f)
    val topBarColor = if (listScrollY < 50) {
        MiuixTheme.colorScheme.surface
    } else {
        val surface = MiuixTheme.colorScheme.surface
        val target = if (isDark) ComposeColor.Black.copy(alpha = 0.7f) else ComposeColor.White.copy(alpha = 0.7f)
        lerp(surface, target, topBarColorProgress)
    }
    val topAppBarColors = BlurDefaults.blurColors(
        blendColors = listOf(
            if (isDark) BlendColorEntry(ComposeColor.Black.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
            else BlendColorEntry(ComposeColor.White.copy(alpha = blurAlpha), BlurBlendMode.SrcOver)
        ),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = if (blurAlpha > 0f) {
                    Modifier.textureBlur(backdrop = backdrop, shape = RectangleShape, colors = topAppBarColors)
                } else {
                    Modifier
                },
                color = topBarColor,
                title = "应用偏好设置",
                largeTitle = "应用偏好设置",
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 20.dp,
                navigationIcon = {
                    IconButton(onClick = {
                        onBack()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .collect { offset ->
                        listScrollY = offset
                    }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding(),
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
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val navBarEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = "标准导航栏",
                                        selected = navBarStyle == "standard",
                                        onClick = {
                                            settingsViewModel.setNavBarStyle("standard")
                                        }
                                    ),
                                    DropdownItem(
                                        text = "悬浮导航栏",
                                        selected = navBarStyle == "floating",
                                        onClick = {
                                            settingsViewModel.setNavBarStyle("floating")
                                        }
                                    ),
                                )
                            )

                            OverlayDropdownPreference(
                                title = "底栏样式",
                                summary = "选择导航栏的显示样式",
                                entry = navBarEntry,
                                collapseOnSelection = true
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
