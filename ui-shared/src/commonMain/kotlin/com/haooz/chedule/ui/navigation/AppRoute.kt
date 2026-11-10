package com.haooz.chedule.ui.navigation

import com.haooz.chedule.ui.settings.SettingsCategory

/**
 * 应用内子页路由表（单宿主 + 显式路由；iOS 无 Activity，导航状态不能活在 Android back stack 里）。
 *
 * ⚠ [id] 是持久化契约（`rememberSaveable` 进程/配置重建用）：已发布路由的 id 不要改，否则多任务切换回来会落回首页；改 id 必须写迁移。
 *
 * ⚠ 主界面（今日 / 课表）不在此处：它是常驻底座、不参与 save/restore，在此加路由会让非 saveable
 * 状态（`remember { mutableStateOf }`）丢失。要加主 tab 改 `CourseScheduleApp` 的 pager。
 * 例外是 [Settings]（2026-10 起）：它是二级页而不是主 tab，页内状态全是临时 UI 态，出栈销毁无妨。
 */
sealed interface AppRoute {

    /** 持久化标识。已发布的 id 不要改（见接口 KDoc）。 */
    val id: String

    /** 设置（原主 pager 第三页，现为二级页）。 */
    data object Settings : AppRoute {
        override val id: String get() = "settings"
    }

    /** 天气详情页（今日卡天气行的入口）。 */
    data object Weather : AppRoute {
        override val id: String get() = "weather"
    }

    /** 关于页。 */
    data object About : AppRoute {
        override val id: String get() = "about"
    }

    /** 更新日志。 */
    data object Changelog : AppRoute {
        override val id: String get() = "changelog"
    }

    /** 开源协议全文。 */
    data object License : AppRoute {
        override val id: String get() = "license"
    }

    /** 隐私政策全文。 */
    data object PrivacyPolicy : AppRoute {
        override val id: String get() = "privacy_policy"
    }

    /**
     * 设置分类二级页（容器型分类之一，直达型分类没有二级页）。
     *
     * ⚠ [categoryKey] 见 `SettingsCategory.key`；id 形如 `settings_category/course`。
     * 未知 key 与直达型 key 都会被 [fromId] 丢弃（见那里），页面侧再兜底回「课表」。
     */
    data class SettingsCategoryDetail(val categoryKey: String) : AppRoute {
        override val id: String get() = "$SETTINGS_CATEGORY_PREFIX$categoryKey"

        companion object {
            const val SETTINGS_CATEGORY_PREFIX = "settings_category/"
        }
    }

    /** 更新设置（「更新设置」）。 */
    data object UpdateSettings : AppRoute {
        override val id: String get() = "update_settings"
    }

    /** 交流与反馈（「交流与反馈」）。 */
    data object Communication : AppRoute {
        override val id: String get() = "communication"
    }

    /** 本地备份（「本地备份」）。 */
    data object LocalBackup : AppRoute {
        override val id: String get() = "local_backup"
    }

    /** 捐赠支持（「捐赠支持」）。 */
    data object AppreciateAuthor : AppRoute {
        override val id: String get() = "appreciate_author"
    }

    /** 课表导入（「课表导入」）。 */
    data object ScheduleImport : AppRoute {
        override val id: String get() = "schedule_import"
    }

    /** 课表导出（「课表导出」）。 */
    data object ScheduleExport : AppRoute {
        override val id: String get() = "schedule_export"
    }

    /** 课表备份（「课表备份」）。 */
    data object ScheduleBackup : AppRoute {
        override val id: String get() = "schedule_backup"
    }

    /** AI 文本导入（「AI 文本导入」）。 */
    data object AiImport : AppRoute {
        override val id: String get() = "ai_import"
    }

    /** 桌面小部件引导（「桌面小部件」）。 */
    data object WidgetIntro : AppRoute {
        override val id: String get() = "widget_intro"
    }

    /** 课程提醒（「课程提醒」）。 */
    data object CourseReminder : AppRoute {
        override val id: String get() = "course_reminder"
    }

    /** 节假日与调休（「节假日与调休」）。 */
    data object HolidaySettings : AppRoute {
        override val id: String get() = "holiday_settings"
    }

    /** WebDAV 云备份（「WebDAV 云备份」）。 */
    data object WebDavSettings : AppRoute {
        override val id: String get() = "webdav_settings"
    }

    /** 课程时间管理（「课程时间管理」）。 */
    data object CourseTimeSettings : AppRoute {
        override val id: String get() = "course_time_settings"
    }

    /** 课程管理（「课程管理」）。 */
    data object CourseManage : AppRoute {
        override val id: String get() = "course_manage"
    }

    /**
     * 教务系统导入 —— 学校/适配器选择。页面本体见 `ui/screens/EducationalImportScreen.kt`。
     */
    data object EducationalImport : AppRoute {
        override val id: String get() = "educational_import"
    }

    /**
     * 教务导入的网页导入页：选完学校/适配器后从右侧推入（独立一层，返回先回选择页）。
     * 选中项经 `EduImportSelection` 传递：路由只有稳定 id，没有参数通道。
     */
    data object WebImport : AppRoute {
        override val id: String get() = "web_import"
    }

    companion object {
        /** 按 [id] 还原路由；未知 id 返回 null（版本回退/脏数据时由调用方兜底）。 */
        fun fromId(id: String): AppRoute? = when (id) {
            Settings.id -> Settings
            Weather.id -> Weather
            About.id -> About
            Changelog.id -> Changelog
            License.id -> License
            PrivacyPolicy.id -> PrivacyPolicy
            UpdateSettings.id -> UpdateSettings
            Communication.id -> Communication
            LocalBackup.id -> LocalBackup
            AppreciateAuthor.id -> AppreciateAuthor
            ScheduleImport.id -> ScheduleImport
            ScheduleExport.id -> ScheduleExport
            ScheduleBackup.id -> ScheduleBackup
            AiImport.id -> AiImport
            WidgetIntro.id -> WidgetIntro
            CourseReminder.id -> CourseReminder
            HolidaySettings.id -> HolidaySettings
            WebDavSettings.id -> WebDavSettings
            CourseTimeSettings.id -> CourseTimeSettings
            CourseManage.id -> CourseManage
            EducationalImport.id -> EducationalImport
            WebImport.id -> WebImport
            else -> if (id.startsWith(SettingsCategoryDetail.SETTINGS_CATEGORY_PREFIX)) {
                val key = id.removePrefix(SettingsCategoryDetail.SETTINGS_CATEGORY_PREFIX)
                val category = SettingsCategory.fromKey(key)
                // 直达型分类（提醒/小组件）没有二级列表页：主页点它就直接进目标页面，
                // 所以 `settings_category/reminder` 不是合法路由，还原时按未知 id 丢弃
                if (category == null || category.isDirect) null else SettingsCategoryDetail(key)
            } else {
                null
            }
        }
    }
}
