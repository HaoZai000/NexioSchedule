package com.haooz.chedule.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.haooz.chedule.i18n.L10n
import com.haooz.chedule.i18n.PreferenceStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_settings_about
import com.haooz.chedule.uishared.resources.ic_settings_data
import com.haooz.chedule.uishared.resources.ic_settings_personalization
import com.haooz.chedule.uishared.resources.ic_settings_reminders
import com.haooz.chedule.uishared.resources.ic_settings_update
import org.jetbrains.compose.resources.DrawableResource
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Months

// ─────────────────────────────────────────────────────────────────────────────
// 设置目录：分类 / 分组 / 条目三级的静态描述。
// ⚠ 本文件只放数据，不放 @Composable。
// ⚠ **图标只属于一级分类**（产品确认）：二级页的条目一律不带图标，所以 SettingsEntry
//   只有文案，没有 icon/accent —— 不要为了「好看」再给条目加回去。
// ⚠ 所有标题都是 L10n（`SettingsStr.xxx`），调用处必须现场 invoke()，不要 remember 缓存 ——
//   否则切语言后标题不刷新。
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 图标底色：一组固定常量，**不随深浅模式变化**，运行时不做调色，方便视觉回归。
 * ⚠ 色块的唯一取色出口是 SettingsIconBadge，改这里的常量即可让所有设置页生效。
 */
object SettingsAccents {
    val Blue = Color(0xFF3482FF)
    val Green = Color(0xFF1DCD3A)
    val Purple = Color(0xFF7767F9)
    val Red = Color(0xFFFA382E)
    val Orange = Color(0xFFFF8A00)
    val Gray = Color(0xFF8C9DB0)
}

/**
 * 分类图标：两种来源，渲染时统一收敛成 [androidx.compose.ui.graphics.painter.Painter]。
 *
 * - [Vector]：miuix-icons 自带的内置图标（[ImageVector]）；
 * - [Drawable]：`ui-shared/src/commonMain/composeResources/drawable/` 下的自定义图标。
 *
 * ⚠ 只存句柄不存 Painter：`painterResource` 是 `@Composable`，在本文件（纯数据层）调用会破坏
 *   「只放数据」的约定，也会让枚举初始化依赖组合上下文。真正的解析放在 `SettingsIconBadge`。
 * ⚠ 两种来源都是单色矢量，填充色一律由色块决定的 tint 覆盖，不需要 drawable-dark 变体。
 */
sealed interface SettingsIcon {
    /** miuix-icons 内置矢量图标。 */
    data class Vector(val vector: ImageVector) : SettingsIcon

    /** composeResources 里的自定义图标（XML VectorDrawable）。 */
    data class Drawable(val resource: DrawableResource) : SettingsIcon
}

/**
 * 设置主页的大分类 —— 也是全应用**唯一**带彩色图标的一层。
 *
 * 两类分类：
 * - **容器型**（[directRoute] 为 null）：点进去是本页的二级设置列表；
 * - **直达型**（[directRoute] 非 null）：点一下直接进那个页面，不再套一层「只有一行」的列表。
 *   目前是「提醒 → 课程提醒」「小组件 → 桌面小部件」「关于 → 关于页」「捐赠 → 捐赠支持」。
 *   ⚠ **手机**才压路由；**平板右栏**不跳页，直接内嵌同一页面的内容
 *   （见 `SettingsDirectPane`），否则分栏体验会断。
 *
 * ⚠ [key] 是路由持久化契约（`settings_category/<key>`），发布后不要改名。
 * ⚠ 直达型分类没有二级页，`settings_category/reminder` 这类 id 会被 [AppRoute.fromId] 丢弃。
 */
enum class SettingsCategory(
    val key: String,
    val title: L10n,
    val summary: L10n,
    val icon: SettingsIcon,
    val accent: Color,
    /** 非 null = 直达型：主页点它就是进这个页面，没有二级列表。 */
    val directRoute: AppRoute? = null,
) {
    Course(
        "course",
        SettingsStr.catCourse,
        SettingsStr.catCourseSummary,
        SettingsIcon.Vector(MiuixIcons.Months),
        SettingsAccents.Blue,
    ),
    Reminder(
        "reminder",
        SettingsStr.catReminder,
        SettingsStr.catReminderSummary,
        SettingsIcon.Drawable(Res.drawable.ic_settings_reminders),
        SettingsAccents.Red,
        directRoute = AppRoute.CourseReminder,
    ),
    Widget(
        "widget",
        SettingsStr.catWidget,
        SettingsStr.catWidgetSummary,
        SettingsIcon.Vector(MiuixIcons.All),
        SettingsAccents.Green,
        directRoute = AppRoute.WidgetIntro,
    ),
    Personalization(
        "personalization",
        SettingsStr.catPersonalization,
        SettingsStr.catPersonalizationSummary,
        SettingsIcon.Drawable(Res.drawable.ic_settings_personalization),
        SettingsAccents.Purple,
    ),
    Data(
        "data",
        SettingsStr.catData,
        SettingsStr.catDataSummary,
        SettingsIcon.Drawable(Res.drawable.ic_settings_data),
        SettingsAccents.Orange,
    ),
    Sync(
        "sync",
        SettingsStr.catSync,
        SettingsStr.catSyncSummary,
        SettingsIcon.Vector(MiuixIcons.CloudFill),
        SettingsAccents.Blue,
    ),
    Update(
        "update",
        SettingsStr.catUpdate,
        SettingsStr.catUpdateSummary,
        SettingsIcon.Drawable(Res.drawable.ic_settings_update),
        SettingsAccents.Green,
    ),
    About(
        "about",
        SettingsStr.catAbout,
        SettingsStr.catAboutSummary,
        SettingsIcon.Drawable(Res.drawable.ic_settings_about),
        SettingsAccents.Gray,
        directRoute = AppRoute.About,
    ),
    // 「捐赠」紧跟「关于」：同为直达型（手机压 AppreciateAuthor 路由，平板右栏内嵌同一页）
    Donate(
        "donate",
        SettingsStr.catDonate,
        SettingsStr.catDonateSummary,
        SettingsIcon.Vector(MiuixIcons.FavoritesFill),
        SettingsAccents.Red,
        directRoute = AppRoute.AppreciateAuthor,
    ),
    ;

    /** 容器型分类才有的二级分组；直达型恒为空。 */
    val sections: List<SettingsSection>
        get() = SettingsSection.entries.filter { it.category == this && !it.shiftOnly }

    val isDirect: Boolean get() = directRoute != null

    companion object {
        fun fromKey(key: String): SettingsCategory? = entries.firstOrNull { it.key == key }

        /** 「更新」分类只在提供应用内更新通道的平台出现（iOS/鸿蒙走应用商店）。 */
        fun visible(showUpdateSettings: Boolean, isShiftMode: Boolean): List<SettingsCategory> = when {
            isShiftMode -> listOf(Course)
            showUpdateSettings -> entries.toList()
            else -> entries.filter { it != Update }
        }

        /**
         * 主页的卡片分组：一个分组 = 一张 Card。
         * 【课表】【提醒、小组件】【个性化、数据管理、备份与同步】【更新、关于、捐赠】
         * iOS/鸿蒙没有「更新」分类时，末组剩「关于、捐赠」，不会出现空卡。
         */
        fun homeGroups(
            showUpdateSettings: Boolean = true,
            isShiftMode: Boolean = false,
        ): List<List<SettingsCategory>> {
            if (isShiftMode) return listOf(listOf(Course))
            val visibleSet = visible(showUpdateSettings, isShiftMode).toSet()
            return listOf(
                listOf(Course),
                listOf(Reminder, Widget),
                listOf(Personalization, Data, Sync),
                listOf(Update, About, Donate),
            ).map { group -> group.filter { it in visibleSet } }.filter { it.isNotEmpty() }
        }
    }
}

/** 一个分组 = 一张 SmallTitle + Card。title 为 null 时只画卡片、不画标题（沿用旧「其他」两张卡的样式）。 */
enum class SettingsSection(
    val category: SettingsCategory,
    val title: L10n?,
    /** 只在排班模式出现的分组：不参与 [SettingsCategory.sections] 的常规枚举。 */
    val shiftOnly: Boolean = false,
) {
    Semester(SettingsCategory.Course, SettingsStr.secSemester),
    // 上课时间 + 节假日与调休合成一张卡：学期区末尾，单行卡不需要小标题
    ClassTime(SettingsCategory.Course, null),
    CourseDisplay(SettingsCategory.Course, SettingsStr.secCourseDisplay),
    CourseExtra(SettingsCategory.Course, SettingsStr.secCourseExtra),
    // 「其它」组里的第二张卡：开启新学期单独一行一张卡（无标题，紧跟上一张卡）
    CourseMisc(SettingsCategory.Course, null),
    ShiftCompare(SettingsCategory.Course, SettingsStr.selectCompareSchedules, shiftOnly = true),

    // 「个性化」首组不带小标题：主题预览双图直接顶在页首
    Appearance(SettingsCategory.Personalization, null),
    // 与「外观」同属个性化页首组：材质两行直接跟在主题双图下方，不再单独起标题
    Material(SettingsCategory.Personalization, null),
    LanguageHome(SettingsCategory.Personalization, SettingsStr.secLanguageHome),
    Interaction(SettingsCategory.Personalization, SettingsStr.secInteraction),
    ;
    // ⚠ 「更新」「备份与同步」「数据管理」分类没有 SettingsSection：
    //   更新 = 整页内联 UpdateSettingsScreen + 更新历史日志；
    //   备份与同步 = 本地备份整页内容 + 底部 WebDAV 入口；
    //   数据管理 = 整页内联课表导入 + 导出 + 页尾「数据源」分组。
    //   都见 SettingsCategoryPage / 平板右栏的特殊分支，不是行列表。

    val entries: List<SettingsEntry> get() = SettingsEntry.entries.filter { it.section == this }
}

/**
 * 单个设置条目（二级页的一行）。**只有文案，没有图标** —— 图标只属于一级分类。
 *
 * [hiddenInShiftMode]：排班模式下不该出现的项（显示非本周、开启新学期）。
 * 岛光效不在此列 —— 它靠整组隐藏（未开启超级岛时）。
 */
enum class SettingsEntry(
    val section: SettingsSection,
    val title: L10n,
    val hiddenInShiftMode: Boolean = false,
) {
    StartDate(SettingsSection.Semester, SettingsStr.startDate),
    CurrentWeek(SettingsSection.Semester, SettingsStr.currentWeek),
    TotalWeeks(SettingsSection.Semester, SettingsStr.totalWeeksTitle),

    CourseTime(SettingsSection.ClassTime, SettingsStr.courseTimeManagement),
    Holiday(SettingsSection.ClassTime, SettingsStr.holidayAndWorkswap),

    SmartWeekend(SettingsSection.CourseDisplay, SettingsStr.smartWeekend),
    ShowNonCurrentWeek(
        SettingsSection.CourseDisplay,
        SettingsStr.showNonCurrentWeek,
        hiddenInShiftMode = true,
    ),

    ShiftMode(SettingsSection.CourseExtra, SettingsStr.shiftMode, hiddenInShiftMode = true),

    NewSemester(SettingsSection.CourseMisc, SettingsStr.newSemester, hiddenInShiftMode = true),

    AutoDark(SettingsSection.Appearance, PreferenceStr.autoDarkMode),
    Monet(SettingsSection.Appearance, PreferenceStr.monetColor),

    AppMaterial(SettingsSection.Material, PreferenceStr.appMaterialEffect),
    TrueProgressiveBlur(SettingsSection.Material, PreferenceStr.trueProgressiveBlur),

    Language(SettingsSection.LanguageHome, SettingsStr.appLanguage),
    DefaultHomepage(SettingsSection.LanguageHome, PreferenceStr.defaultHomepage),

    PredictiveBack(SettingsSection.Interaction, PreferenceStr.predictiveBack),
    Haptic(SettingsSection.Interaction, PreferenceStr.hapticFeedback),
    HideFromRecents(SettingsSection.Interaction, PreferenceStr.hideFromRecents),
    ;

    val category: SettingsCategory get() = section.category
}

/** 某个分类下要渲染的分组（排班模式在「课表」末尾追加对比课表卡片）。 */
fun SettingsCategory.visibleSections(isShiftMode: Boolean): List<SettingsSection> {
    val base = sections
    val list = if (isShiftMode && this == SettingsCategory.Course) base + SettingsSection.ShiftCompare else base
    // 整组条目都被排班模式过滤掉时连卡带标题一起收起，避免留下空卡片
    return list.filter { it == SettingsSection.ShiftCompare || it.visibleEntries(isShiftMode).isNotEmpty() }
}

/** 某个分组下要渲染的条目。 */
fun SettingsSection.visibleEntries(isShiftMode: Boolean): List<SettingsEntry> =
    entries.filter { !(isShiftMode && it.hiddenInShiftMode) }
