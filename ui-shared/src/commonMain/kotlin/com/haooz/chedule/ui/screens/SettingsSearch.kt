package com.haooz.chedule.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.AboutStr
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.L10n
import com.haooz.chedule.i18n.PreferenceStr
import com.haooz.chedule.i18n.ReminderStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.i18n.TimeCfgStr
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.LocalTopBarMaterial
import top.yukonga.miuix.kmp.layout.TopBarMaterialState
import top.yukonga.miuix.kmp.layout.rememberTopBarMaterialState
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState

/** 搜索框整块占用的高度（上下间距 + 输入框最小高度），列表按它让位。 */
internal val SettingsSearchBarBlockHeight = 59.dp

/**
 * 设置页搜索状态。
 *
 * 状态要提升到路由层：搜索框必须画在 `DocumentPageScaffold` 的页面采样层
 * **之外**（`headerOverlay`），否则它会成为被采样子树的一部分，采样到自己。
 */
class SettingsSearchState {
    var query by mutableStateOf("")
    var expanded by mutableStateOf(false)
    /** 列表是否已滚起（由列表侧写入，搜索框据此决定是否显材质） */
    var listScrolled by mutableStateOf(false)
}

@Composable
internal fun rememberSettingsSearchState(): SettingsSearchState =
    remember { SettingsSearchState() }

/** 无材质档：搜索框玻璃恒定关闭 */
private val NoGlassAlpha: () -> Float = { 0f }

/**
 * 搜索框本体：由路由挂在 `DocumentPageScaffold.headerOverlay` 上，
 * 采样页面级 [backdrop]，位置与教务导入页一致（标题下方、跟随折叠）。
 */
@Composable
internal fun SettingsSearchHeader(
    state: SettingsSearchState,
    backdrop: GlassBackdrop,

) {
    val glassDisabled = AppMaterialSettings.isNoneTier()
    // 优先用页面下发的那一份（与返回键同帧）；独立使用时退回自己的判据
    val overScrollState = LocalOverScrollState.current
    val material = LocalTopBarMaterial.current
        ?: rememberTopBarMaterialState {
            state.listScrolled || overScrollState.offset < 0f
        }
    // 无材质档：搜索框本来就没有玻璃，恒定 0
    val glassAlpha: () -> Float = if (glassDisabled) NoGlassAlpha else material.alpha
    val horizontalPadding = if (isTabletWidth()) 20.dp else 16.dp
    SettingsSearchField(
        query = state.query,
        onQueryChange = { state.query = it },
        expanded = state.expanded,
        onExpandedChange = { state.expanded = it },
        backdrop = if (glassDisabled) null else backdrop,
        backdropAlpha = glassAlpha(),
        shadowAlpha = glassAlpha(),
        modifier = Modifier.padding(
            // SearchBar 的输入框自带 10dp 内边距，这里补足差值，让胶囊与下方卡片左右对齐
            start = (horizontalPadding - 10.dp).coerceAtLeast(0.dp),
            top = 8.dp,
            end = (horizontalPadding - 10.dp).coerceAtLeast(0.dp),
            bottom = 6.dp,
        ),
    )
}

/**
 * 搜索结果的入口分组：与设置首页的入口一一对应，决定结果卡片顺序与左侧图标。
 */
internal enum class SettingsSearchGroup(val label: L10n) {
    Schedule(SettingsStr.scheduleSettings),
    Reminder(SettingsStr.courseReminder),
    Widget(SettingsStr.homeScreenWidget),
    Personalization(SettingsStr.appPreferences),
    ImportExport(SettingsStr.importExport),
    Backup(SettingsStr.scheduleBackup),
    Update(SettingsStr.updateSettings),
    About(SettingsStr.aboutApp),
    Donate(SettingsStr.destAppreciate),
    Feedback(SettingsStr.destCommunication),
}

/**
 * 一条可搜索的设置项。
 *
 * @param ancestors 面包屑上级：分组名 +（可选的）页内小节名。
 * @param route 命中后打开的设置页。
 */
internal data class SettingsSearchEntry(
    val group: SettingsSearchGroup,
    val title: String,
    val summary: String?,
    val ancestors: List<String>,
    val route: AppRoute,
) {
    /** 副标题：完整面包屑（含本项标题），如「蓝牙/高级设置/蓝牙设备黑名单」。 */
    val breadcrumb: String get() = (ancestors + title).joinToString("/")
}

private fun entry(
    group: SettingsSearchGroup,
    title: String,
    route: AppRoute,
    summary: String? = null,
    section: String? = null,
): SettingsSearchEntry = SettingsSearchEntry(
    group = group,
    title = title,
    summary = summary,
    ancestors = listOfNotNull(group.label(), section),
    route = route,
)

/**
 * 设置项搜索索引。按设置首页入口顺序排列；每个入口下覆盖该页的可见设置项与开关。
 * 文案全部取 i18n，切换语言后重建即可跟随。
 */
internal fun settingsSearchIndex(): List<SettingsSearchEntry> = buildList {
    // ── 课表管理 ──
    val schedule = SettingsSearchGroup.Schedule
    add(entry(schedule, SettingsStr.scheduleSettings(), AppRoute.ScheduleSettings))
    add(entry(schedule, SettingsStr.startDate(), AppRoute.ScheduleSettings))
    add(entry(schedule, SettingsStr.currentWeek(), AppRoute.ScheduleSettings))
    add(entry(schedule, SettingsStr.totalWeeksTitle(), AppRoute.ScheduleSettings))
    add(entry(schedule, TimeCfgStr.sectionCountTitle(), AppRoute.ScheduleSettings))
    add(
        entry(
            schedule, SettingsStr.classTime(), AppRoute.CourseTimeSettings,
            summary = SettingsStr.courseTimeSummary(),
        )
    )
    add(entry(schedule, SettingsStr.holidayAndWorkswap(), AppRoute.HolidaySettings))
    add(
        entry(
            schedule, SettingsStr.smartWeekend(), AppRoute.ScheduleSettings,
            summary = SettingsStr.smartWeekendSummary(),
        )
    )
    add(entry(schedule, SettingsStr.showNonCurrentWeek(), AppRoute.ScheduleSettings))
    add(
        entry(
            schedule, SettingsStr.shiftMode(), AppRoute.ScheduleSettings,
            summary = SettingsStr.shiftModeSummary(),
        )
    )
    add(
        entry(
            schedule, SettingsStr.newSemester(), AppRoute.ScheduleSettings,
            summary = SettingsStr.newSemesterSummary(),
        )
    )

    // ── 课程提醒 ──
    val reminder = SettingsSearchGroup.Reminder
    add(
        entry(
            reminder, SettingsStr.courseReminder(), AppRoute.CourseReminder,
            summary = SettingsStr.courseReminderSummary(),
        )
    )
    add(entry(reminder, ReminderStr.enableReminders(), AppRoute.CourseReminder))
    add(
        entry(
            reminder, ReminderStr.preClassReminder(), AppRoute.CourseReminder,
            summary = ReminderStr.preClassReminderSummary(),
        )
    )
    add(entry(reminder, ReminderStr.reminderLeadTime(), AppRoute.CourseReminder))
    add(
        entry(
            reminder, ReminderStr.nextDayReminder(), AppRoute.CourseReminder,
            summary = ReminderStr.nextDayReminderSummary(),
        )
    )
    add(entry(reminder, ReminderStr.reminderTime(), AppRoute.CourseReminder))
    add(
        entry(
            reminder, ReminderStr.inClassReminder(), AppRoute.CourseReminder,
            summary = ReminderStr.inClassReminderSummary(),
        )
    )
    add(entry(reminder, ReminderStr.reminderTiming(), AppRoute.CourseReminder))
    add(
        entry(
            reminder, ReminderStr.autoDnd(), AppRoute.CourseReminder,
            summary = ReminderStr.autoDndSummary(),
        )
    )
    add(entry(reminder, ReminderStr.dndModeTitle(), AppRoute.CourseReminder))
    add(entry(reminder, ReminderStr.thumbAbbreviation(), AppRoute.CourseReminder))
    add(entry(reminder, ReminderStr.abbreviationMethod(), AppRoute.CourseReminder))
    add(
        entry(
            reminder, ReminderStr.viewAbbreviationPreview(), AppRoute.CourseReminder,
            summary = ReminderStr.viewAbbreviationPreviewSummary(),
        )
    )

    // ── 桌面小部件 ──
    val widget = SettingsSearchGroup.Widget
    add(entry(widget, SettingsStr.homeScreenWidget(), AppRoute.WidgetIntro))
    add(entry(widget, SettingsStr.addToHomeScreen(), AppRoute.WidgetIntro))

    // ── 应用个性化 ──
    val personalization = SettingsSearchGroup.Personalization
    add(entry(personalization, SettingsStr.appPreferences(), AppRoute.PreferenceSettings))
    add(entry(personalization, PreferenceStr.lightMode(), AppRoute.PreferenceSettings))
    add(entry(personalization, PreferenceStr.darkMode(), AppRoute.PreferenceSettings))
    add(entry(personalization, PreferenceStr.autoDarkMode(), AppRoute.PreferenceSettings))
    add(
        entry(
            personalization, PreferenceStr.monetColor(), AppRoute.PreferenceSettings,
            summary = PreferenceStr.monetColorSummary(),
        )
    )
    add(entry(personalization, PreferenceStr.appMaterialEffect(), AppRoute.PreferenceSettings))
    add(entry(personalization, PreferenceStr.trueProgressiveBlur(), AppRoute.PreferenceSettings))
    add(
        entry(
            personalization, PreferenceStr.superIslandGlow(), AppRoute.PreferenceSettings,
            summary = PreferenceStr.superIslandGlowSummary(),
        )
    )
    val appSettingsSection = PreferenceStr.appSettings()
    add(
        entry(
            personalization, PreferenceStr.defaultHomepage(), AppRoute.PreferenceSettings,
            summary = PreferenceStr.defaultHomepageSummary(), section = appSettingsSection,
        )
    )
    add(
        entry(
            personalization, PreferenceStr.predictiveBack(), AppRoute.PreferenceSettings,
            summary = PreferenceStr.predictiveBackSummary(), section = appSettingsSection,
        )
    )
    add(
        entry(
            personalization, PreferenceStr.hapticFeedback(), AppRoute.PreferenceSettings,
            summary = PreferenceStr.hapticFeedbackSummary(), section = appSettingsSection,
        )
    )
    add(
        entry(
            personalization, PreferenceStr.hideFromRecents(), AppRoute.PreferenceSettings,
            summary = PreferenceStr.hideFromRecentsSummary(), section = appSettingsSection,
        )
    )
    val dataSourceSection = PreferenceStr.dataSources()
    add(
        entry(
            personalization, PreferenceStr.eduRepoSource(), AppRoute.PreferenceSettings,
            summary = PreferenceStr.eduRepoSourceSummary(), section = dataSourceSection,
        )
    )
    add(
        entry(
            personalization, PreferenceStr.weatherDataSource(), AppRoute.PreferenceSettings,
            section = dataSourceSection,
        )
    )
    add(
        entry(
            personalization, PreferenceStr.holidayDataSource(), AppRoute.PreferenceSettings,
            summary = PreferenceStr.holidayDataSourceSummary(), section = dataSourceSection,
        )
    )

    // ── 导入导出 ──
    val importExport = SettingsSearchGroup.ImportExport
    val importMethodsSection = BackupStr.importMethods()
    val shareCodeSection = BackupStr.shareCodeSection()
    val fileSection = BackupStr.fileSection()
    val exportSection = BackupStr.exportSchedule()
    val exportFormatSection = BackupStr.exportFormat()
    add(entry(importExport, SettingsStr.importExport(), AppRoute.ImportExport))
    add(
        entry(
            importExport, BackupStr.aiTextImport(), AppRoute.ImportExport,
            summary = BackupStr.aiTextImportSummary(), section = importMethodsSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.educationalImport(), AppRoute.ImportExport,
            summary = BackupStr.educationalImportSummary(), section = importMethodsSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.shareCodeImport(), AppRoute.ImportExport,
            summary = BackupStr.shareCodeImportSummary(), section = shareCodeSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.wakeUpCodeImport(), AppRoute.ImportExport,
            summary = BackupStr.wakeUpCodeImportSummary(), section = shareCodeSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.starLinkCodeImport(), AppRoute.ImportExport,
            summary = BackupStr.starLinkCodeImportSummary(), section = shareCodeSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.jsonFileImport(), AppRoute.ImportExport,
            summary = BackupStr.jsonFileImportSummary(), section = fileSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.icsFileImport(), AppRoute.ImportExport,
            summary = BackupStr.icsFileImportSummary(), section = fileSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.jsonExport(), AppRoute.ImportExport,
            summary = BackupStr.jsonExportSummary(), section = exportFormatSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.icsExport(), AppRoute.ImportExport,
            summary = BackupStr.icsExportSummary(), section = exportFormatSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.shareCodeExport(), AppRoute.ImportExport,
            summary = BackupStr.shareCodeExportSummary(), section = exportSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.syncToCalendar(), AppRoute.ImportExport,
            summary = BackupStr.syncToCalendarSummary(), section = exportSection,
        )
    )
    add(
        entry(
            importExport, BackupStr.exportToWearable(), AppRoute.ImportExport,
            summary = BackupStr.exportToWearableSummary(), section = exportSection,
        )
    )

    // ── 备份与同步 ──
    val backup = SettingsSearchGroup.Backup
    val backupSection = BackupStr.backupSection()
    add(entry(backup, SettingsStr.scheduleBackup(), AppRoute.ScheduleBackup))
    add(
        entry(
            backup, BackupStr.localBackup(), AppRoute.LocalBackup,
            summary = BackupStr.localBackupSummary(), section = backupSection,
        )
    )
    add(
        entry(
            backup, BackupStr.webDavBackup(), AppRoute.WebDavSettings,
            summary = BackupStr.webDavBackupSummary(), section = backupSection,
        )
    )

    // ── 更新 ──
    val update = SettingsSearchGroup.Update
    add(entry(update, SettingsStr.updateSettings(), AppRoute.UpdateSettings))
    add(entry(update, AboutStr.checkForUpdates(), AppRoute.UpdateSettings))

    // ── 关于应用 ──
    val about = SettingsSearchGroup.About
    add(entry(about, SettingsStr.aboutApp(), AppRoute.About))
    add(entry(about, CommonStr.appLanguage(), AppRoute.About))
    add(entry(about, AboutStr.projectRepo(), AppRoute.About))
    add(entry(about, AboutStr.changelog(), AppRoute.Changelog))
    add(entry(about, AboutStr.license(), AppRoute.License))
    add(entry(about, AboutStr.privacyPolicy(), AppRoute.PrivacyPolicy))

    // ── 捐赠支持 ──
    add(entry(SettingsSearchGroup.Donate, SettingsStr.destAppreciate(), AppRoute.AppreciateAuthor))

    // ── 问题反馈 ──
    add(entry(SettingsSearchGroup.Feedback, SettingsStr.destCommunication(), AppRoute.Communication))
}

/**
 * 按关键词过滤并分组：标题命中排在 summary 命中之前，分组保持设置首页顺序。
 */
internal fun searchSettings(
    query: String,
    index: List<SettingsSearchEntry> = settingsSearchIndex(),
): List<Pair<SettingsSearchGroup, List<SettingsSearchEntry>>> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return index
        .filter {
            it.title.contains(q, ignoreCase = true) ||
                it.summary?.contains(q, ignoreCase = true) == true
        }
        // 稳定排序：标题命中在前，组内仍保持索引顺序
        .sortedBy { if (it.title.contains(q, ignoreCase = true)) 0 else 1 }
        .groupBy { it.group }
        .toList()
}

/** 设置页搜索框：与教务导入页同一套 SearchBar + InputField 外观。 */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    backdropAlpha: Float = 0f,
    shadowAlpha: Float = 0f,
) {
    val isLightTheme = !isAppDarkTheme()
    SearchBar(
        modifier = modifier,
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onExpandedChange(false) },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                label = SettingsStr.searchSettings(),
                backdrop = backdrop,
                backdropAlpha = backdropAlpha,
                shadowAlpha = shadowAlpha,
                isLightTheme = { isLightTheme },
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        actionIcon = MiuixIcons.Normal.Close,
        onActionClick = {
            onQueryChange("")
            onExpandedChange(false)
        },
        backdrop = backdrop,
        backdropAlpha = backdropAlpha,
    )
}

/**
 * 搜索结果：按设置首页入口分组，每组一张卡片；组内首行带入口图标，副标题为完整面包屑。
 */
@Composable
internal fun SettingsSearchResults(
    groups: List<Pair<SettingsSearchGroup, List<SettingsSearchEntry>>>,
    query: String,
    topPadding: Dp,
    horizontalPadding: Dp,
    scrollBehavior: SharedScrollBehavior?,
    onEntryClick: (SettingsSearchEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .collapsibleTopInset(scrollBehavior),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = topPadding,
            bottom = 60.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (query.isNotBlank() && groups.isEmpty()) {
            item(key = "search_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = SettingsStr.searchNoResults(),
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        groups.forEach { (group, entries) ->
            item(key = "search_${group.name}") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        entries.forEachIndexed { i, entry ->
                            SettingsSearchResultRow(
                                entry = entry,
                                query = query,
                                showIcon = i == 0,
                                onClick = { onEntryClick(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchResultRow(
    entry: SettingsSearchEntry,
    query: String,
    showIcon: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            SettingsSearchGroupIcon(group = entry.group)
            Spacer(Modifier.width(16.dp))
        } else {
            // 组内后续行与首行标题左对齐：图标 28dp + 间距 16dp
            Spacer(Modifier.width(44.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedTitle(entry.title, query),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.breadcrumb,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 入口分组图标，复用设置首页的那套彩色底板。 */
@Composable
private fun SettingsSearchGroupIcon(group: SettingsSearchGroup) {
    when (group) {
        SettingsSearchGroup.Schedule -> ScheduleSettingsIcon()
        SettingsSearchGroup.Reminder -> CourseReminderIcon()
        SettingsSearchGroup.Widget -> HomeScreenWidgetIcon()
        SettingsSearchGroup.Personalization -> PersonalizationIcon()
        SettingsSearchGroup.ImportExport -> ImportExportIcon()
        SettingsSearchGroup.Backup -> BackupSyncIcon()
        SettingsSearchGroup.Update -> UpdateSettingsIcon()
        SettingsSearchGroup.About -> AboutAppIcon()
        SettingsSearchGroup.Donate -> DonateIcon()
        SettingsSearchGroup.Feedback -> CommunicationIcon()
    }
}

/** 标题中命中的关键词用主色高亮（忽略大小写，多处命中都标）。 */
@Composable
private fun highlightedTitle(title: String, query: String): AnnotatedString {
    val q = query.trim()
    val accent = MiuixTheme.colorScheme.primary
    return remember(title, q, accent) {
        if (q.isEmpty()) {
            AnnotatedString(title)
        } else {
            buildAnnotatedString {
                var start = 0
                while (true) {
                    val index = title.indexOf(q, startIndex = start, ignoreCase = true)
                    if (index < 0) {
                        append(title.substring(start))
                        break
                    }
                    append(title.substring(start, index))
                    withStyle(SpanStyle(color = accent)) {
                        append(title.substring(index, index + q.length))
                    }
                    start = index + q.length
                }
            }
        }
    }
}
