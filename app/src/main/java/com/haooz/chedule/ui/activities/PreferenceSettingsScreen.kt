/** 偏好设置页面 - Screen */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.haooz.chedule.R
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.utils.overScrollVertical
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun PreferenceSettingsScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
) {
    var listScrollY by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val eduPrefs = remember { context.getSharedPreferences("edu_import_prefs", Context.MODE_PRIVATE) }
    var repoUrl by remember { mutableStateOf(eduPrefs.getString("repo_url", "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse") ?: "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse") }

    val themePrefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(themePrefs.getString("theme_mode", "system") ?: "system") }
    // 自动（跟随系统）模式下，用系统当前主题决定高亮哪个色框
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    val settingsViewModel = remember { SettingsViewModel(context.applicationContext as android.app.Application) }
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
    val reminderPrefs = remember { context.getSharedPreferences("course_reminder_prefs", Context.MODE_PRIVATE) }
    var islandExpandGlowEnabled by remember {
        mutableStateOf(reminderPrefs.getBoolean("island_expand_glow_enabled", true))
    }
    val appPrefs = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    var hideBackground by remember {
        mutableStateOf(appPrefs.getBoolean("hide_background", false))
    }

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
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    )

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThemeSelectionBox(
                            label = "浅色模式",
                            drawableRes = R.drawable.theme_preview_light,
                            selected = !effectiveDark,
                            onClick = onClick@ {
                                // 自动模式下点了与当前系统一致的框，保持自动
                                if (themeMode == "system" && !systemDark) return@onClick
                                themeMode = "light"
                                themePrefs.edit { putString("theme_mode", "light") }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeSelectionBox(
                            label = "深色模式",
                            drawableRes = R.drawable.theme_preview_night,
                            selected = effectiveDark,
                            onClick = onClick@ {
                                // 自动模式下点了与当前系统一致的框，保持自动
                                if (themeMode == "system" && systemDark) return@onClick
                                themeMode = "dark"
                                themePrefs.edit { putString("theme_mode", "dark") }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = "自动切换深色模式",
                                checked = themeMode == "system",
                                onCheckedChange = { on ->
                                    if (on) {
                                        themeMode = "system"
                                        themePrefs.edit { putString("theme_mode", "system") }
                                    } else {
                                        themeMode = "light"
                                        themePrefs.edit { putString("theme_mode", "light") }
                                    }
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
                            if (islandNotification) {
                                SwitchPreference(
                                    title = "小米超级岛光效",
                                    summary = "在小米超级岛展开态显示流动光效",
                                    checked = islandExpandGlowEnabled,
                                    onCheckedChange = {
                                        islandExpandGlowEnabled = it
                                        reminderPrefs.edit { putBoolean("island_expand_glow_enabled", it) }
                                    }
                                )
                            }
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

                            OverlayDropdownMenu(
                                title = "默认首页",
                                summary = "首次启动时默认显示的页面",
                                entry = homepageEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
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
                            SwitchPreference(
                                title = "隐藏后台",
                                summary = "返回桌面时，隐藏应用的最近任务卡片",
                                checked = hideBackground,
                                onCheckedChange = {
                                    hideBackground = it
                                    appPrefs.edit { putBoolean("hide_background", it) }
                                    MainActivity.setTaskExcludedFromRecents(context, it)
                                }
                            )
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

                            OverlayDropdownMenu(
                                title = "数据仓库源",
                                summary = "更新教务系统数据源的仓库",
                                entry = repoEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 深浅色选择框：内嵌图片，点击手动切换 */
@Composable
private fun ThemeSelectionBox(
    label: String,
    drawableRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 选中态：沿图片外圈空出 6dp，绘制一圈蓝色描边（G2 超椭圆圆角）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .squircleBorder(
                    width = 3.dp,
                    color = if (selected) MiuixTheme.colorScheme.primary else Color.Transparent,
                    cornerRadius = 25.dp
                )
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .squircleClip(20.dp)
            ) {
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
