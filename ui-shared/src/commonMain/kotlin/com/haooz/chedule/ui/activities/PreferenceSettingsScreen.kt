/** 偏好设置页面 - Screen */
package com.haooz.chedule.ui.activities

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.LanguagePref
import com.haooz.chedule.i18n.LanguageStore
import com.haooz.chedule.i18n.PreferenceStr
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.components.CardExpand
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.theme.KEY_MONET_COLOR
import com.haooz.chedule.ui.theme.LocalSystemDarkTheme
import com.haooz.chedule.ui.theme.isMonetColorAvailable
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.theme_preview_light
import com.haooz.chedule.uishared.resources.theme_preview_night
import com.haooz.chedule.viewmodel.settingsViewModel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.kyant.capsule.continuousBorder
import com.kyant.capsule.continuousClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun PreferenceSettingsScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    /** 是否已授予定位权限（Android 宿主注入权限检查；桌面/iOS 恒 false） */
    hasLocationPermission: () -> Boolean = { false },
    /** 清空天气缓存（Android 宿主注入；桌面/iOS 空实现） */
    onInvalidateWeatherCache: () -> Unit = {},
    /** 隐藏最近任务卡片（Android 专有能力；宿主注入） */
    onSetTaskExcludedFromRecents: (Boolean) -> Unit = {},
) {

    val eduPrefs = remember { AppStorage.store("edu_import_prefs") }
    var repoUrl by remember {
        mutableStateOf(
            eduPrefs.getString("repo_url", "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse")
        )
    }

    var holidaySource by remember { mutableStateOf(HolidayManager.holidaySource()) }

    val weatherPrefs = remember { AppStorage.store("weather_prefs") }
    // 旧值 "caiyun"/"itboy" 不再有对应实现，Source.from 会回落到融合源
    var weatherSource by remember { mutableStateOf(weatherPrefs.getString("weather_source", "fusion")) }
    // 天气定位摘要：有定位权限显示当前定位，否则回退显示上次定位
    val lastLocationName = weatherPrefs.getStringOrNull("last_location_name")
    val weatherHasPermission = hasLocationPermission()
    val weatherLocationSummary = when {
        weatherHasPermission ->
            if (lastLocationName != null) PreferenceStr.currentLocationS(lastLocationName)
            else PreferenceStr.currentLocationNone()
        lastLocationName != null -> PreferenceStr.lastLocationS(lastLocationName)
        else -> PreferenceStr.weatherNeedsLocation()
    }

    val themePrefs = remember { AppStorage.store("app_theme_prefs") }
    var themeMode by remember { mutableStateOf(themePrefs.getString("theme_mode", "system")) }
    var monetEnabled by remember { mutableStateOf(themePrefs.getBoolean(KEY_MONET_COLOR, false)) }
    // 自动（跟随系统）模式下，用系统当前主题决定高亮哪个色框
    val systemDark = LocalSystemDarkTheme.current ?: isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    // SettingsViewModel 已去掉 Application（提醒时刻算法抠进 :core 后不再需要 Context），统一走 viewModel() 进 ViewModelStore
    val settingsViewModel = settingsViewModel()
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
    val reminderPrefs = remember { AppStorage.store("course_reminder_prefs") }
    // 默认值必须与 IslandNotificationHelper.DEFAULT_ISLAND_EXPAND_GLOW 保持一致
    var islandExpandGlowEnabled by remember {
        mutableStateOf(reminderPrefs.getBoolean("island_expand_glow_enabled", false))
    }
    val appPrefs = remember { AppStorage.store("app_preferences") }
    var hideBackground by remember {
        mutableStateOf(appPrefs.getBoolean("hide_background", false))
    }
    var hapticFeedbackEnabled by remember {
        mutableStateOf(
            appPrefs.getBoolean(com.haooz.chedule.ui.theme.KEY_HAPTIC_FEEDBACK, true)
        )
    }
    var appMaterialLevel by remember {
        mutableStateOf(
            appPrefs.getString(
                AppMaterialSettings.KEY_APP_MATERIAL,
                AppMaterialSettings.BALANCED
            )
        )
    }
    // 统一限制：模糊能力不可用（Android API < 31）一律锁进无材质档；桌面 / iOS 恒支持不锁
    val materialLocked = !com.kyant.backdrop.isRenderEffectSupported()
    val effectiveMaterialLevel = if (materialLocked) {
        AppMaterialSettings.NONE
    } else {
        appMaterialLevel
    }
    // 启动时同步到全局，后续材质效果直接读 AppMaterialSettings.level
    LaunchedEffect(Unit) {
        AppMaterialSettings.apply(effectiveMaterialLevel)
    }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .collapsibleTopInset(scrollBehavior)
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThemeSelectionBox(
                            label = PreferenceStr.lightMode(),
                            drawableRes = Res.drawable.theme_preview_light,
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
                            label = PreferenceStr.darkMode(),
                            drawableRes = Res.drawable.theme_preview_night,
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
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = PreferenceStr.autoDarkMode(),
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
                            SwitchPreference(
                                title = PreferenceStr.monetColor(),
                                summary = if (isMonetColorAvailable()) {
                                    PreferenceStr.monetColorSummary()
                                } else {
                                    PreferenceStr.monetColorUnavailable()
                                },
                                checked = monetEnabled && isMonetColorAvailable(),
                                enabled = isMonetColorAvailable(),
                                onCheckedChange = {
                                    monetEnabled = it
                                    themePrefs.edit { putBoolean(KEY_MONET_COLOR, it) }
                                }
                            )
                        }
                    }
                }
                // 材质行被平台锁定时隐藏；锁定时若也没有岛光开关，整张卡片不占位（否则多出一格 12dp 间距）
                if (AppMaterialSettings.lockedLevel == null || islandNotification) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 平台锁定档位时不给切换入口（鸿蒙：玻璃能力固定）
                                if (AppMaterialSettings.lockedLevel == null) {
                                    val appMaterialEntry = DropdownEntry(
                                        items = (if (materialLocked) {
                                            // 锁定态只留无材质一项，其余档位点不到
                                            listOf(
                                                AppMaterialSettings.NONE to
                                                    AppMaterialSettings.labelOf(AppMaterialSettings.NONE)
                                            )
                                        } else {
                                            AppMaterialSettings.entries
                                        }).map { (value, label) ->
                                            DropdownItem(
                                                text = label,
                                                selected = effectiveMaterialLevel == value,
                                                onClick = {
                                                    FeatureLog.preference("app_material=$value")
                                                    appMaterialLevel = value
                                                    AppMaterialSettings.apply(value)
                                                    appPrefs.edit {
                                                        putString(AppMaterialSettings.KEY_APP_MATERIAL, value)
                                                    }
                                                }
                                            )
                                        }
                                    )
                                    OverlayDropdownMenu(
                                        title = PreferenceStr.appMaterialEffect(),
                                        // summary 为空时自动显示当前选中档位；锁定态给出原因
                                        summary = if (materialLocked) PreferenceStr.materialLockedSummary() else null,
                                        entry = appMaterialEntry,
                                        collapseOnSelection = true,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = liquidGlassDropdownColors,
                                    )
                                }
                                CardExpand(visible = islandNotification) {
                                    SwitchPreference(
                                        title = PreferenceStr.superIslandGlow(),
                                        summary = PreferenceStr.superIslandGlowSummary(),
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
                }

                item {
                    SmallTitle(
                        text = PreferenceStr.appSettings(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 直接读 AppLanguage 的快照状态：切换后本行的选中态即时刷新
                            val languagePref = AppLanguage.pref
                            val languageEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = CommonStr.followSystem(),
                                        selected = languagePref == LanguagePref.SYSTEM,
                                        onClick = {
                                            AppLanguage.set(LanguagePref.SYSTEM)
                                            LanguageStore.save(LanguagePref.SYSTEM)
                                        }
                                    ),
                                    DropdownItem(
                                        text = CommonStr.simplifiedChinese(),
                                        selected = languagePref == LanguagePref.ZH,
                                        onClick = {
                                            AppLanguage.set(LanguagePref.ZH)
                                            LanguageStore.save(LanguagePref.ZH)
                                        }
                                    ),
                                    DropdownItem(
                                        text = CommonStr.english(),
                                        selected = languagePref == LanguagePref.EN,
                                        onClick = {
                                            AppLanguage.set(LanguagePref.EN)
                                            LanguageStore.save(LanguagePref.EN)
                                        }
                                    ),
                                )
                            )

                            OverlayDropdownMenu(
                                title = CommonStr.language(),
                                summary = CommonStr.languageSummary(),
                                entry = languageEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val homepageEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = CommonStr.today(),
                                        selected = defaultHomepage == "今日",
                                        onClick = {
                                            FeatureLog.preference("default_homepage=今日")
                                            settingsViewModel.setDefaultHomepage("今日")
                                        }
                                    ),
                                    DropdownItem(
                                        text = PreferenceStr.schedule(),
                                        selected = defaultHomepage == "课程表",
                                        onClick = {
                                            FeatureLog.preference("default_homepage=课程表")
                                            settingsViewModel.setDefaultHomepage("课程表")
                                        }
                                    ),
                                )
                            )

                            OverlayDropdownMenu(
                                title = PreferenceStr.defaultHomepage(),
                                summary = PreferenceStr.defaultHomepageSummary(),
                                entry = homepageEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            var predictiveBackEnabled by remember {
                                mutableStateOf(
                                    appPrefs.getBoolean(
                                        PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION,
                                        true
                                    )
                                )
                            }
                            SwitchPreference(
                                title = PreferenceStr.predictiveBack(),
                                summary = PreferenceStr.predictiveBackSummary(),
                                checked = predictiveBackEnabled,
                                onCheckedChange = {
                                    predictiveBackEnabled = it
                                    PredictiveBackSettings.enabled = it
                                    appPrefs.edit {
                                        putBoolean(
                                            PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION,
                                            it
                                        )
                                    }
                                }
                            )
                            SwitchPreference(
                                title = PreferenceStr.hapticFeedback(),
                                summary = PreferenceStr.hapticFeedbackSummary(),
                                checked = hapticFeedbackEnabled,
                                onCheckedChange = {
                                    hapticFeedbackEnabled = it
                                    appPrefs.edit {
                                        putBoolean(
                                            com.haooz.chedule.ui.theme.KEY_HAPTIC_FEEDBACK,
                                            it
                                        )
                                    }
                                }
                            )
                            SwitchPreference(
                                title = PreferenceStr.hideFromRecents(),
                                summary = PreferenceStr.hideFromRecentsSummary(),
                                checked = hideBackground,
                                onCheckedChange = {
                                    hideBackground = it
                                    appPrefs.edit { putBoolean("hide_background", it) }
                                    onSetTaskExcludedFromRecents(it)
                                }
                            )
                        }
                    }
                }

                item {
                    SmallTitle(
                        text = PreferenceStr.dataSources(),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val repoEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = PreferenceStr.forkSource(),
                                        selected = repoUrl == "https://gitee.com/com_haooz_account/shiguang_warehouse",
                                        onClick = {
                                            repoUrl = "https://gitee.com/com_haooz_account/shiguang_warehouse"
                                            eduPrefs.edit { putString("repo_url", repoUrl) }
                                        }
                                    ),
                                    DropdownItem(
                                        text = "GitHub",
                                        selected = repoUrl == "https://github.com/XingHeYuZhuan/shiguang_warehouse",
                                        onClick = {
                                            repoUrl = "https://github.com/XingHeYuZhuan/shiguang_warehouse"
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
                                title = PreferenceStr.eduRepoSource(),
                                summary = PreferenceStr.eduRepoSourceSummary(),
                                entry = repoEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )

                            val weatherSourceEntry = DropdownEntry(
                                items = listOf(
                                    "fusion" to WeatherStr.sourceFusion(),
                                    "xiaomi" to WeatherStr.sourceXiaomi(),
                                    "weathercn" to WeatherStr.sourceWeatherCn(),
                                    "openmeteo" to WeatherStr.sourceOpenMeteo(),
                                ).map { (key, label) ->
                                    DropdownItem(
                                        text = label,
                                        selected = weatherSource == key,
                                        onClick = {
                                            weatherSource = key
                                            weatherPrefs.edit { putString("weather_source", key) }
                                            onInvalidateWeatherCache()
                                        }
                                    )
                                }
                            )

                            OverlayDropdownMenu(
                                title = PreferenceStr.weatherDataSource(),
                                summary = weatherLocationSummary,
                                entry = weatherSourceEntry,
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )

                            val holidaySourceEntry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = PreferenceStr.stateCouncil(),
                                        selected = holidaySource == HolidayManager.SOURCE_HOLIDAY_CALENDAR,
                                        onClick = {
                                            holidaySource = HolidayManager.SOURCE_HOLIDAY_CALENDAR
                                            HolidayManager.setHolidaySource(holidaySource)
                                        }
                                    ),
                                    DropdownItem(
                                        text = "APIHubs",
                                        selected = holidaySource == HolidayManager.SOURCE_APIHUBS,
                                        onClick = {
                                            holidaySource = HolidayManager.SOURCE_APIHUBS
                                            HolidayManager.setHolidaySource(holidaySource)
                                        }
                                    ),
                                )
                            )

                            OverlayDropdownMenu(
                                title = PreferenceStr.holidayDataSource(),
                                summary = PreferenceStr.holidayDataSourceSummary(),
                                entry = holidaySourceEntry,
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
    drawableRes: DrawableResource,
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
                .continuousBorder(
                    width = 3.dp,
                    color = if (selected) MiuixTheme.colorScheme.primary else Color.Transparent,
                    cornerRadius = 26.dp
                )
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .continuousClip(20.dp)
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
