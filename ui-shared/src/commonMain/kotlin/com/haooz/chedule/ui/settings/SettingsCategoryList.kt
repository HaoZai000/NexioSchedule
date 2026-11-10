package com.haooz.chedule.ui.settings

// ⚠ 本文件不得出现 android.* / androidx.activity；平台能力全部经 SettingsContentHost 注入。

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.i18n.AppLanguage
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.LanguagePref
import com.haooz.chedule.i18n.LanguageStore
import com.haooz.chedule.i18n.PreferenceStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.i18n.WeatherStr
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.screens.getDaysInMonth
import com.haooz.chedule.ui.screens.parseDate
import com.haooz.chedule.ui.theme.KEY_HAPTIC_FEEDBACK
import com.haooz.chedule.ui.theme.KEY_MONET_COLOR
import com.haooz.chedule.ui.theme.LocalSystemDarkTheme
import com.haooz.chedule.ui.theme.isMonetColorAvailable
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.theme_preview_light
import com.haooz.chedule.uishared.resources.theme_preview_night
import com.haooz.softglass.shape.continuousBorder
import com.haooz.softglass.shape.continuousClip
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 二级页的弹窗状态。放在列表外层持有，避免写进 `item {}` 被 LazyColumn 回收。
 */
@Stable
class SettingsDialogState(initialThemeMode: String) {
    var showStartDate by mutableStateOf(false)
    var showCurrentWeek by mutableStateOf(false)
    var showTotalWeeks by mutableStateOf(false)
    var showNewSemester by mutableStateOf(false)
    var newSemesterName by mutableStateOf("")
    /** 进入排班模式确认（原先在设置主页的「模式」卡上，随入口一起搬进「课表 → 其它」） */
    var showShiftConfirm by mutableStateOf(false)
    /** 主题模式在「外观预览框」与「自动深色」两处共用，必须单一状态源，否则点框后开关不跟着变。 */
    var themeMode by mutableStateOf(initialThemeMode)
    var tempYear by mutableIntStateOf(2024)
    var tempMonth by mutableIntStateOf(1)
    var tempDay by mutableIntStateOf(1)
    /** 打开弹窗时的时间值，用来把年份区间撑开到已有数据（旧数据可能早于 2024） */
    var initYear by mutableIntStateOf(2024)
    var tempCurrentWeek by mutableIntStateOf(1)
    var tempTotalWeeks by mutableIntStateOf(20)
}

/**
 * 一个分类的二级页内容 —— 手机分类路由页与平板右栏**共用这一份**。
 *
 * 只负责「按 section 铺卡片」，不参与顶栏 / 背景 / 分栏；那些交给调用方。
 */
@Composable
fun SettingsCategoryList(
    category: SettingsCategory,
    host: SettingsContentHost,
    contentPadding: PaddingValues,
    listModifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val dialogs = remember {
        val prefs = AppStorage.store("app_theme_prefs")
        SettingsDialogState(prefs.getString("theme_mode", "system"))
    }
    val hapticFeedback = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = listModifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            category.visibleSections(host.isShiftMode)
                .forEach { section ->
                    item(key = section.name) {
                        if (section == SettingsSection.ShiftCompare) {
                            ShiftCompareCard(host)
                        } else {
                            SettingsSectionBlock(section, host, dialogs)
                        }
                    }
                }
            if (host.isShiftMode) {
                item(key = "shift_exit") { ExitShiftModeButton(host) }
            }
        }
        SettingsDialogs(dialogs, host)
    }
}

@Composable
private fun SettingsSectionBlock(
    section: SettingsSection,
    host: SettingsContentHost,
    dialogs: SettingsDialogState,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        section.title?.let { SmallTitle(text = it()) }
        // 外观分组的主题预览双图在卡片之上
        if (section == SettingsSection.Appearance) {
            ThemePreviewPicker(dialogs)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                section.visibleEntries(host.isShiftMode).forEach { entry ->
                    SettingsEntryRow(entry, host, dialogs)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 行分发
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsEntryRow(
    entry: SettingsEntry,
    host: SettingsContentHost,
    dialogs: SettingsDialogState,
) {
    when (entry) {
        // ── 课表 · 学期 ──
        SettingsEntry.StartDate -> {
            val classStartTime by host.courseViewModel.classStartTime.collectAsState()
            ArrowRow(
                entry = entry,
                endText = classStartTime,
                holdDown = dialogs.showStartDate,
                onClick = {
                    FeatureLog.t("设置", "start_date_dialog")
                    val (y, m, d) = parseDate(classStartTime)
                    dialogs.initYear = y
                    dialogs.tempYear = y
                    dialogs.tempMonth = m
                    dialogs.tempDay = d
                    dialogs.showStartDate = true
                },
            )
        }

        SettingsEntry.CurrentWeek -> {
            val currentWeek by host.courseViewModel.currentWeek.collectAsState()
            val totalWeeks by host.courseViewModel.totalWeeks.collectAsState()
            val isSemesterStarted by host.courseViewModel.isSemesterStarted.collectAsState()
            ArrowRow(
                entry = entry,
                endText = when {
                    !isSemesterStarted -> SettingsStr.notStarted()
                    currentWeek > totalWeeks -> SettingsStr.onBreak()
                    else -> CommonStr.weekN(currentWeek)
                },
                holdDown = dialogs.showCurrentWeek,
                onClick = {
                    FeatureLog.t("设置", "current_week_dialog")
                    // 未开始学期时 currentWeek 可能是 0：滚轮会被 coerce 到 1 显示，
                    // 这里也必须抬到 1，否则「看着是第 1 周、点确定却写回 0」
                    dialogs.tempCurrentWeek = currentWeek.coerceIn(1, totalWeeks)
                    dialogs.showCurrentWeek = true
                },
            )
        }

        SettingsEntry.TotalWeeks -> {
            val totalWeeks by host.courseViewModel.totalWeeks.collectAsState()
            ArrowRow(
                entry = entry,
                endText = SettingsStr.totalWeeksValue(totalWeeks),
                holdDown = dialogs.showTotalWeeks,
                onClick = {
                    FeatureLog.t("设置", "total_weeks_dialog")
                    dialogs.tempTotalWeeks = totalWeeks
                    dialogs.showTotalWeeks = true
                },
            )
        }

        SettingsEntry.NewSemester -> ArrowRow(
            entry = entry,
            summary = SettingsStr.newSemesterSummary(),
            holdDown = dialogs.showNewSemester,
            onClick = {
                FeatureLog.t("设置", "new_semester_dialog")
                dialogs.newSemesterName = ""
                dialogs.showNewSemester = true
            },
        )

        // ── 课表 · 课表显示 ──
        SettingsEntry.SmartWeekend -> {
            val checked by host.settingsViewModel.smartWeekend.collectAsState()
            SwitchPreference(
                title = entry.title(),
                summary = SettingsStr.smartWeekendSummary(),
                checked = checked,
                onCheckedChange = { host.settingsViewModel.setSmartWeekend(it) },
            )
        }

        SettingsEntry.ShowNonCurrentWeek -> {
            val checked by host.settingsViewModel.showNonCurrentWeek.collectAsState()
            SwitchPreference(
                title = entry.title(),
                checked = checked,
                onCheckedChange = { host.settingsViewModel.setShowNonCurrentWeek(it) },
            )
        }

        // ── 课表 · 上课时间 ──
        SettingsEntry.CourseTime -> ArrowRow(
            entry = entry,
            summary = SettingsStr.courseTimeSummary(),
            onClick = {
                FeatureLog.timeConfig("open")
                host.onNavigate(AppRoute.CourseTimeSettings)
            },
        )

        // ── 课表 · 其它 ──
        SettingsEntry.Holiday -> ArrowRow(
            entry = entry,
            onClick = {
                FeatureLog.holiday("open")
                host.onNavigate(AppRoute.HolidaySettings)
            },
        )

        SettingsEntry.ShiftMode -> ArrowRow(
            entry = entry,
            summary = SettingsStr.shiftModeSummary(),
            holdDown = dialogs.showShiftConfirm,
            onClick = { dialogs.showShiftConfirm = true },
        )

        // ── 个性化 · 外观 ──
        SettingsEntry.AutoDark -> {
            val prefs = remember { AppStorage.store("app_theme_prefs") }
            SwitchPreference(
                title = entry.title(),
                checked = dialogs.themeMode == "system",
                onCheckedChange = { on ->
                    val next = if (on) "system" else "light"
                    dialogs.themeMode = next
                    prefs.edit { putString("theme_mode", next) }
                },
            )
        }

        SettingsEntry.Monet -> {
            val prefs = remember { AppStorage.store("app_theme_prefs") }
            val available = isMonetColorAvailable()
            var enabled by remember { mutableStateOf(prefs.getBoolean(KEY_MONET_COLOR, false)) }
            SwitchPreference(
                title = entry.title(),
                summary = if (available) PreferenceStr.monetColorSummary() else PreferenceStr.monetColorUnavailable(),
                checked = enabled && available,
                enabled = available,
                onCheckedChange = {
                    enabled = it
                    prefs.edit { putBoolean(KEY_MONET_COLOR, it) }
                },
            )
        }

        // ── 个性化 · 材质与动效 ──
        SettingsEntry.AppMaterial -> AppMaterialRow(entry, host)

        SettingsEntry.TrueProgressiveBlur -> {
            val prefs = remember { AppStorage.store("app_preferences") }
            var enabled by remember {
                mutableStateOf(prefs.getBoolean(AppMaterialSettings.KEY_TRUE_PROGRESSIVE_BLUR, true))
            }
            val materialLocked = !com.haooz.softglass.platform.isRenderEffectSupported()
            val level = if (materialLocked) AppMaterialSettings.NONE else AppMaterialSettings.level
            SwitchPreference(
                title = entry.title(),
                summary = if (level == AppMaterialSettings.NONE) PreferenceStr.trueProgressiveBlurDisabled() else null,
                checked = enabled,
                enabled = level != AppMaterialSettings.NONE,
                onCheckedChange = { on ->
                    FeatureLog.preference("true_progressive_blur=$on")
                    enabled = on
                    AppMaterialSettings.applyTrueProgressiveBlur(on)
                    prefs.edit { putBoolean(AppMaterialSettings.KEY_TRUE_PROGRESSIVE_BLUR, on) }
                },
            )
        }

        // ── 个性化 · 语言与首页 ──
        SettingsEntry.Language -> LanguageRow(entry, host)
        SettingsEntry.DefaultHomepage -> DefaultHomepageRow(entry, host)

        // ── 个性化 · 交互 ──
        SettingsEntry.PredictiveBack -> {
            val prefs = remember { AppStorage.store("app_preferences") }
            var enabled by remember {
                mutableStateOf(prefs.getBoolean(PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION, true))
            }
            SwitchPreference(
                title = entry.title(),
                summary = PreferenceStr.predictiveBackSummary(),
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    PredictiveBackSettings.enabled = on
                    prefs.edit { putBoolean(PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION, on) }
                },
            )
        }

        SettingsEntry.Haptic -> {
            val prefs = remember { AppStorage.store("app_preferences") }
            var enabled by remember { mutableStateOf(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)) }
            SwitchPreference(
                title = entry.title(),
                summary = PreferenceStr.hapticFeedbackSummary(),
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    prefs.edit { putBoolean(KEY_HAPTIC_FEEDBACK, on) }
                },
            )
        }

        SettingsEntry.HideFromRecents -> {
            val prefs = remember { AppStorage.store("app_preferences") }
            var enabled by remember { mutableStateOf(prefs.getBoolean("hide_background", false)) }
            SwitchPreference(
                title = entry.title(),
                summary = PreferenceStr.hideFromRecentsSummary(),
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    prefs.edit { putBoolean("hide_background", on) }
                    host.onSetTaskExcludedFromRecents(on)
                },
            )
        }

        // 「数据管理」分类整页内联（导入 + 导出 + 数据源），见 SettingsCategoryPage 的特殊分支。
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 行实现
// ─────────────────────────────────────────────────────────────────────────────

/** 通用「可点击 → 下一级」行。endText 走右侧说明文字，summary 走标题下方副标题。 */
@Composable
private fun ArrowRow(
    entry: SettingsEntry,
    summary: String? = null,
    endText: String? = null,
    holdDown: Boolean = false,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = entry.title(),
        summary = summary,
        endActions = {
            if (endText != null) {
                Text(
                    text = endText,
                    fontSize = 14.5.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.End,
                )
            }
        },
        holdDownState = holdDown,
        onClick = onClick,
    )
}

@Composable
private fun ThemePreviewPicker(dialogs: SettingsDialogState) {
    val prefs = remember { AppStorage.store("app_theme_prefs") }
    val systemDark = LocalSystemDarkTheme.current ?: isSystemInDarkTheme()
    val effectiveDark = when (dialogs.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeSelectionBox(
            label = PreferenceStr.lightMode(),
            drawableRes = Res.drawable.theme_preview_light,
            selected = !effectiveDark,
            onClick = {
                // 自动模式下点了与当前系统一致的框，保持自动
                if (dialogs.themeMode == "system" && !systemDark) return@ThemeSelectionBox
                dialogs.themeMode = "light"
                prefs.edit { putString("theme_mode", "light") }
            },
            modifier = Modifier.weight(1f),
        )
        ThemeSelectionBox(
            label = PreferenceStr.darkMode(),
            drawableRes = Res.drawable.theme_preview_night,
            selected = effectiveDark,
            onClick = {
                if (dialogs.themeMode == "system" && systemDark) return@ThemeSelectionBox
                dialogs.themeMode = "dark"
                prefs.edit { putString("theme_mode", "dark") }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 深浅色选择框：内嵌图片，点击手动切换（与旧偏好设置页同一套视觉）。 */
@Composable
private fun ThemeSelectionBox(
    label: String,
    drawableRes: DrawableResource,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .continuousBorder(
                    width = 3.dp,
                    color = if (selected) MiuixTheme.colorScheme.primary else Color.Transparent,
                    cornerRadius = 26.dp,
                )
                .padding(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .continuousClip(20.dp),
            ) {
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppMaterialRow(
    entry: SettingsEntry,
    host: SettingsContentHost,
) {
    // 平台锁定档位时不给切换入口（鸿蒙：玻璃能力固定）
    if (AppMaterialSettings.lockedLevel != null) return
    val prefs = remember { AppStorage.store("app_preferences") }
    val materialLocked = !com.haooz.softglass.platform.isRenderEffectSupported()
    var level by remember {
        mutableStateOf(prefs.getString(AppMaterialSettings.KEY_APP_MATERIAL, AppMaterialSettings.BALANCED))
    }
    val effective = if (materialLocked) AppMaterialSettings.NONE else level
    val items = (if (materialLocked) {
        listOf(AppMaterialSettings.NONE to AppMaterialSettings.labelOf(AppMaterialSettings.NONE))
    } else {
        AppMaterialSettings.entries
    }).map { (value, label) ->
        DropdownItem(
            text = label,
            selected = effective == value,
            onClick = {
                FeatureLog.preference("app_material=$value")
                level = value
                AppMaterialSettings.apply(value)
                prefs.edit { putString(AppMaterialSettings.KEY_APP_MATERIAL, value) }
            },
        )
    }
    OverlayDropdownMenu(
        title = entry.title(),
        // summary 为空时自动显示当前选中档位；锁定态给出原因
        summary = if (materialLocked) PreferenceStr.materialLockedSummary() else null,
        entry = DropdownEntry(items = items),
        collapseOnSelection = true,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        dropdownColors = liquidGlassDropdownColors(),
    )
}

@Composable
private fun LanguageRow(
    entry: SettingsEntry,
    host: SettingsContentHost,
) {
    val languagePref = AppLanguage.pref
    val items = listOf(
        DropdownItem(
            text = CommonStr.followSystem(),
            selected = languagePref == LanguagePref.SYSTEM,
            onClick = {
                AppLanguage.set(LanguagePref.SYSTEM)
                LanguageStore.save(LanguagePref.SYSTEM)
            },
        ),
        DropdownItem(
            text = CommonStr.simplifiedChinese(),
            selected = languagePref == LanguagePref.ZH,
            onClick = {
                AppLanguage.set(LanguagePref.ZH)
                LanguageStore.save(LanguagePref.ZH)
            },
        ),
        DropdownItem(
            text = CommonStr.english(),
            selected = languagePref == LanguagePref.EN,
            onClick = {
                AppLanguage.set(LanguagePref.EN)
                LanguageStore.save(LanguagePref.EN)
            },
        ),
    )
    OverlayDropdownMenu(
        title = entry.title(),
        entry = DropdownEntry(items = items),
        collapseOnSelection = true,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        dropdownColors = liquidGlassDropdownColors(),
    )
}

@Composable
private fun DefaultHomepageRow(
    entry: SettingsEntry,
    host: SettingsContentHost,
) {
    val current by host.settingsViewModel.defaultHomepage.collectAsState()
    // ⚠ 存储值是中文字面量「今日」/「课程表」，不要翻译、不要改枚举（见 CourseRepository.KEY_DEFAULT_HOMEPAGE）
    val items = listOf(
        DropdownItem(
            text = CommonStr.today(),
            selected = current == "今日",
            onClick = {
                FeatureLog.preference("default_homepage=今日")
                host.settingsViewModel.setDefaultHomepage("今日")
            },
        ),
        DropdownItem(
            text = PreferenceStr.schedule(),
            selected = current == "课程表",
            onClick = {
                FeatureLog.preference("default_homepage=课程表")
                host.settingsViewModel.setDefaultHomepage("课程表")
            },
        ),
    )
    OverlayDropdownMenu(
        title = entry.title(),
        entry = DropdownEntry(items = items),
        collapseOnSelection = true,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        dropdownColors = liquidGlassDropdownColors(),
    )
}

@Composable
private fun EduRepoSourceRow(
    title: String,
    host: SettingsContentHost,
) {
    val prefs = remember { AppStorage.store("edu_import_prefs") }
    var repoUrl by remember {
        mutableStateOf(prefs.getString("repo_url", "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"))
    }
    val items = listOf(
        DropdownItem(
            text = PreferenceStr.forkSource(),
            selected = repoUrl == "https://gitee.com/com_haooz_account/shiguang_warehouse",
            onClick = {
                repoUrl = "https://gitee.com/com_haooz_account/shiguang_warehouse"
                prefs.edit { putString("repo_url", repoUrl) }
            },
        ),
        DropdownItem(
            text = "GitHub",
            selected = repoUrl == "https://github.com/XingHeYuZhuan/shiguang_warehouse",
            onClick = {
                repoUrl = "https://github.com/XingHeYuZhuan/shiguang_warehouse"
                prefs.edit { putString("repo_url", repoUrl) }
            },
        ),
        DropdownItem(
            text = "Gitee",
            selected = repoUrl == "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse",
            onClick = {
                repoUrl = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
                prefs.edit { putString("repo_url", repoUrl) }
            },
        ),
    )
    OverlayDropdownMenu(
        title = title,
        summary = PreferenceStr.eduRepoSourceSummary(),
        entry = DropdownEntry(items = items),
        collapseOnSelection = true,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        dropdownColors = liquidGlassDropdownColors(),
    )
}

@Composable
private fun WeatherSourceRow(
    title: String,
    host: SettingsContentHost,
) {
    val prefs = remember { AppStorage.store("weather_prefs") }
    // 旧值 "caiyun"/"itboy" 不再有对应实现，Source.from 会回落到融合源
    var source by remember { mutableStateOf(prefs.getString("weather_source", "fusion")) }
    val lastLocationName = prefs.getStringOrNull("last_location_name")
    val hasPermission = host.hasLocationPermission()
    val summary = when {
        hasPermission ->
            if (lastLocationName != null) PreferenceStr.currentLocationS(lastLocationName)
            else PreferenceStr.currentLocationNone()
        lastLocationName != null -> PreferenceStr.lastLocationS(lastLocationName)
        else -> PreferenceStr.weatherNeedsLocation()
    }
    val items = listOf(
        "fusion" to WeatherStr.sourceFusion(),
        "xiaomi" to WeatherStr.sourceXiaomi(),
        "weathercn" to WeatherStr.sourceWeatherCn(),
        "openmeteo" to WeatherStr.sourceOpenMeteo(),
    ).map { (key, label) ->
        DropdownItem(
            text = label,
            selected = source == key,
            onClick = {
                source = key
                prefs.edit { putString("weather_source", key) }
                host.onInvalidateWeatherCache()
            },
        )
    }
    OverlayDropdownMenu(
        title = title,
        summary = summary,
        entry = DropdownEntry(items = items),
        collapseOnSelection = true,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        dropdownColors = liquidGlassDropdownColors(),
    )
}

@Composable
private fun HolidaySourceRow(
    title: String,
    host: SettingsContentHost,
) {
    var source by remember { mutableStateOf(HolidayManager.holidaySource()) }
    val items = listOf(
        DropdownItem(
            text = PreferenceStr.stateCouncil(),
            selected = source == HolidayManager.SOURCE_HOLIDAY_CALENDAR,
            onClick = {
                source = HolidayManager.SOURCE_HOLIDAY_CALENDAR
                HolidayManager.setHolidaySource(source)
            },
        ),
        DropdownItem(
            text = "APIHubs",
            selected = source == HolidayManager.SOURCE_APIHUBS,
            onClick = {
                source = HolidayManager.SOURCE_APIHUBS
                HolidayManager.setHolidaySource(source)
            },
        ),
    )
    OverlayDropdownMenu(
        title = title,
        summary = PreferenceStr.holidayDataSourceSummary(),
        entry = DropdownEntry(items = items),
        collapseOnSelection = true,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        dropdownColors = liquidGlassDropdownColors(),
    )
}

@Composable
private fun ShiftCompareCard(host: SettingsContentHost) {
    val scheduleNames by host.scheduleViewModel.scheduleNames.collectAsState()
    val scheduleSummaries by host.scheduleViewModel.scheduleSummaries.collectAsState()
    val selected by host.shiftViewModel.shiftSelectedSchedules.collectAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = SettingsStr.selectCompareSchedules())
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                scheduleNames.forEach { name ->
                    CheckboxPreference(
                        title = name,
                        summary = scheduleSummaries[name] ?: "",
                        checked = name in selected,
                        onCheckedChange = { checked ->
                            val next = if (checked) selected + name else selected - name
                            host.shiftViewModel.setShiftSelectedSchedules(next)
                        },
                        checkboxLocation = CheckboxLocation.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExitShiftModeButton(host: SettingsContentHost) {
    val hapticFeedback = LocalHapticFeedback.current
    val isDark = isAppDarkTheme()
    top.yukonga.miuix.kmp.basic.Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .height(50.dp),
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            host.onExitShiftMode()
        },
        colors = if (isDark) {
            ButtonDefaults.buttonColors(color = Color(0xFF181818))
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            text = SettingsStr.exitShiftMode(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFF44336),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 弹窗
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsDialogs(dialogs: SettingsDialogState, host: SettingsContentHost) {
    val hapticFeedback = LocalHapticFeedback.current

    OverlayDialog(
        title = SettingsStr.enterShiftMode(),
        summary = SettingsStr.enterShiftModeSummary(),
        show = dialogs.showShiftConfirm,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        onDismissRequest = { dialogs.showShiftConfirm = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = CommonStr.cancel(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        dialogs.showShiftConfirm = false
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = CommonStr.ok(),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        dialogs.showShiftConfirm = false
                        host.onEnterShiftMode()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    OverlayDialog(
        title = SettingsStr.newSemester(),
        summary = SettingsStr.newSemesterDialogSummary(),
        show = dialogs.showNewSemester,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        onDismissRequest = {
            dialogs.showNewSemester = false
            dialogs.newSemesterName = ""
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NativeMiuixTextField(
                value = dialogs.newSemesterName,
                onValueChange = { dialogs.newSemesterName = it },
                label = SettingsStr.newScheduleNameLabel(),
                modifier = Modifier.fillMaxWidth(),
                requestFocus = dialogs.showNewSemester,
            )
            DialogButtons(
                confirmText = SettingsStr.create(),
                onCancel = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    dialogs.showNewSemester = false
                    dialogs.newSemesterName = ""
                },
                onConfirm = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    val name = dialogs.newSemesterName.trim()
                    when {
                        // 名称空着点「创建」以前是静默无反应，看着像按钮坏了
                        name.isEmpty() -> showToast(SettingsStr.emptyNameToast())
                        name in host.scheduleViewModel.scheduleNames.value ->
                            showToast(SettingsStr.duplicateNameToast())

                        else -> {
                            host.scheduleViewModel.createNewSemesterSchedule(name)
                            dialogs.showNewSemester = false
                            dialogs.newSemesterName = ""
                            showToast(SettingsStr.createdToast(name))
                        }
                    }
                },
            )
        }
    }

    OverlayDialog(
        title = SettingsStr.startDate(),
        show = dialogs.showStartDate,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        onDismissRequest = { dialogs.showStartDate = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val maxDaysInMonth = remember(dialogs.tempYear, dialogs.tempMonth) {
                getDaysInMonth(dialogs.tempYear, dialogs.tempMonth)
            }
            LaunchedEffect(maxDaysInMonth) {
                if (dialogs.tempDay > maxDaysInMonth) dialogs.tempDay = maxDaysInMonth
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberPicker(
                    value = dialogs.tempYear,
                    onValueChange = { dialogs.tempYear = it },
                    // 年份超出静态区间时滚轮会静默夹到边界，按打开时的值把范围撑开
                    range = minOf(2024, dialogs.initYear)..maxOf(2030, dialogs.initYear),
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f),
                )
                NumberPicker(
                    value = dialogs.tempMonth,
                    onValueChange = { dialogs.tempMonth = it },
                    range = 1..12,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    label = { SettingsStr.monthN(it) },
                    wrapAround = true,
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f),
                )
                NumberPicker(
                    value = dialogs.tempDay,
                    onValueChange = { dialogs.tempDay = it },
                    range = 1..maxDaysInMonth,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    label = { CommonStr.dateN(it) },
                    wrapAround = true,
                    textStyle = MiuixTheme.textStyles.title2,
                    modifier = Modifier.weight(1f),
                )
            }
            DialogButtons(
                confirmText = CommonStr.ok(),
                onCancel = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    dialogs.showStartDate = false
                },
                onConfirm = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    // ⚠ 手写 padStart（String.format 是 JVM 专有）
                    val date = "${dialogs.tempYear.toString().padStart(4, '0')}/" +
                        "${dialogs.tempMonth.toString().padStart(2, '0')}/" +
                        dialogs.tempDay.toString().padStart(2, '0')
                    host.courseViewModel.setClassStartTime(date)
                    dialogs.showStartDate = false
                },
            )
        }
    }

    OverlayDialog(
        title = SettingsStr.selectCurrentWeek(),
        show = dialogs.showCurrentWeek,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        onDismissRequest = { dialogs.showCurrentWeek = false },
    ) {
        val totalWeeks by host.courseViewModel.totalWeeks.collectAsState()
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NumberPicker(
                value = dialogs.tempCurrentWeek,
                onValueChange = { dialogs.tempCurrentWeek = it },
                range = 1..totalWeeks.coerceAtLeast(1),
                visibleItemCount = 3,
                itemHeight = 60.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )
            DialogButtons(
                confirmText = CommonStr.ok(),
                onCancel = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    dialogs.showCurrentWeek = false
                },
                onConfirm = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    host.courseViewModel.setCurrentWeek(dialogs.tempCurrentWeek)
                    dialogs.showCurrentWeek = false
                },
            )
        }
    }

    OverlayDialog(
        title = SettingsStr.selectTotalWeeks(),
        show = dialogs.showTotalWeeks,
        liquidGlassBackdrop = host.liquidGlassBackdrop,
        onDismissRequest = { dialogs.showTotalWeeks = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NumberPicker(
                value = dialogs.tempTotalWeeks,
                onValueChange = { dialogs.tempTotalWeeks = it },
                range = 1..30,
                visibleItemCount = 3,
                itemHeight = 60.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )
            DialogButtons(
                confirmText = CommonStr.ok(),
                onCancel = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    dialogs.showTotalWeeks = false
                },
                onConfirm = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    host.courseViewModel.setTotalWeeks(dialogs.tempTotalWeeks)
                    dialogs.showTotalWeeks = false
                },
            )
        }
    }
}

@Composable
private fun DialogButtons(
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(text = CommonStr.cancel(), onClick = onCancel, modifier = Modifier.weight(1f))
        TextButton(
            text = confirmText,
            onClick = onConfirm,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.weight(1f),
        )
    }
}

/** 液态玻璃效果的透明下拉颜色（设置页浮在壁纸上时用）。 */
@Composable
private fun liquidGlassDropdownColors() = DropdownDefaults.dropdownColors(
    containerColor = Color.Transparent,
    selectedContainerColor = Color.Transparent,
)

/**
 * 手机端的分类二级页外壳：给 [SettingsCategoryList] 配上 Scaffold 内边距与折叠顶栏联动。
 * 平板右栏不用这个外壳 —— 它自己管滚动与顶部玻璃层。
 */
@Composable
fun SettingsCategoryPage(
    category: SettingsCategory,
    host: SettingsContentHost,
    scrollBehavior: SharedScrollBehavior? = null,
) {
    val hPad = if (isTabletWidth()) 20.dp else 16.dp
    val listModifier = remember(scrollBehavior) {
        scrollBehavior?.let {
            Modifier
                .collapsibleTopInset(it)
                .nestedScroll(it.nestedScrollConnection)
        } ?: Modifier
    }
    Scaffold(topBar = {}) { paddingValues ->
        when (category) {
            SettingsCategory.Update -> {
                // 「更新」二级页不是行列表：整页内联更新设置 + 历史日志（页内自带滚动与内边距）。
                // ⚠ scrollBehavior 必须传下去，否则顶栏大标题不会随列表滚动折叠。
                UpdateSettingsScreen(
                    scrollBehavior = scrollBehavior,
                    liquidGlassBackdrop = host.liquidGlassBackdrop,
                    updateController = host.updateController,
                    changelogInline = true,
                )
            }

            SettingsCategory.Sync -> {
                // 「备份与同步」二级页 = 本地备份整页内容（备份能力已全部内联，不再有包装页）；
                // 顶部「本地备份」小标题，页尾「同步」小标题 + WebDAV 入口（WebDAV 保持独立页）。
                LocalBackupScreen(
                    scrollBehavior = scrollBehavior,
                    liquidGlassBackdrop = host.liquidGlassBackdrop,
                    backupFileController = host.backupFileController,
                    onHolidayDataChanged = host.onHolidayDataChanged,
                    listHeader = { SmallTitle(text = SettingsStr.destLocalBackup()) },
                    listFooter = { SyncWebDavEntryCard(host) },
                )
            }

            SettingsCategory.Data -> {
                // 「数据管理」二级页 = 课表导入 + 导出整页内联（不再有「课表导入/导出」三级包装页），
                // 页尾挂「数据源」分组。
                BackupAndMigrationScreen(
                    scrollBehavior = scrollBehavior,
                    liquidGlassBackdrop = host.liquidGlassBackdrop,
                    mode = ScheduleDataManageMode.Import,
                    alsoExport = true,
                    scheduleFileController = host.dataManage.scheduleFileController,
                    onHolidayDataChanged = host.onHolidayDataChanged,
                    onFetchShareCode = host.dataManage.onFetchShareCode,
                    onImportThirdParty = host.dataManage.onImportThirdParty,
                    onShareSchedule = host.dataManage.onShareSchedule,
                    onExportToWearable = host.dataManage.onExportToWearable,
                    onSyncToCalendar = host.dataManage.onSyncToCalendar,
                    listFooter = { DataSourcesSectionCard(host) },
                )
            }

            else -> {
                SettingsCategoryList(
                    category = category,
                    host = host,
                    contentPadding = PaddingValues(
                        start = hPad,
                        end = hPad,
                        top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight,
                        bottom = 60.dp,
                    ),
                    listModifier = listModifier,
                )
            }
        }
    }
}

/** 「数据管理」页尾：「数据源」小标题 + 教务仓库 / 天气 / 节假日三个数据源行。 */
@Composable
fun DataSourcesSectionCard(host: SettingsContentHost) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = SettingsStr.secDataSources())
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                EduRepoSourceRow(PreferenceStr.eduRepoSource(), host)
                WeatherSourceRow(PreferenceStr.weatherDataSource(), host)
                HolidaySourceRow(PreferenceStr.holidayDataSource(), host)
            }
        }
    }
}

/** 「备份与同步」页尾：「同步」小标题 + WebDAV 入口（整页最底端，WebDAV 保持独立页）。 */
@Composable
fun SyncWebDavEntryCard(host: SettingsContentHost) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = SettingsStr.secSync())
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = SettingsStr.destWebDav(),
                    onClick = { host.onNavigate(AppRoute.WebDavSettings) },
                )
            }
        }
    }
}
