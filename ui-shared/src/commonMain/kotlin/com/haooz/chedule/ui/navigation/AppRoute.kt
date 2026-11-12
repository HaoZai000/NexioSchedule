package com.haooz.chedule.ui.navigation

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

    /** 课程表。 */
    data object ScheduleSettings : AppRoute {
        override val id: String get() = "schedule_settings"
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

    /** 应用个性化。 */
    data object PreferenceSettings : AppRoute {
        override val id: String get() = "preference_settings"
    }

    /** 更新。 */
    data object UpdateSettings : AppRoute {
        override val id: String get() = "update_settings"
    }

    /** 更新设置（更新通道 / 下载源）。 */
    data object UpdatePreferences : AppRoute {
        override val id: String get() = "update_preferences"
    }

    /** 问题反馈（「问题反馈」）。 */
    data object Communication : AppRoute {
        override val id: String get() = "communication"
    }


    /** 捐赠支持（「捐赠支持」）。 */
    data object AppreciateAuthor : AppRoute {
        override val id: String get() = "appreciate_author"
    }

    /** 备份与同步。 */
    data object ScheduleBackup : AppRoute {
        override val id: String get() = "schedule_backup"
    }

    /** 导入导出（顶部胶囊切换）。 */
    data object ImportExport : AppRoute {
        override val id: String get() = "import_export"
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

    /** WebDAV 云同步。 */
    data object WebDavSettings : AppRoute {
        override val id: String get() = "webdav_settings"
    }

    /** 上课时间（「上课时间」）。 */
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

    /** 应用内反馈（提交 Bug / 功能建议，替代原来的腾讯文档外链）。 */
    data object Feedback : AppRoute {
        override val id: String get() = "feedback"
    }

    /** 我的反馈（按 device_id + 可选邮箱查历史提交与状态时间线）。 */
    data object FeedbackHistory : AppRoute {
        override val id: String get() = "feedback_history"
    }

    /** 开发进度（后端维护的 roadmap，App 只读）。 */
    data object FeedbackRoadmap : AppRoute {
        override val id: String get() = "feedback_roadmap"
    }

    companion object {
        /** 按 [id] 还原路由；未知 id 返回 null（版本回退/脏数据时由调用方兜底）。 */
        fun fromId(id: String): AppRoute? = when (id) {
            Settings.id -> Settings
            ScheduleSettings.id -> ScheduleSettings
            Weather.id -> Weather
            About.id -> About
            Changelog.id -> Changelog
            License.id -> License
            PrivacyPolicy.id -> PrivacyPolicy
            PreferenceSettings.id -> PreferenceSettings
            UpdateSettings.id -> UpdateSettings
            UpdatePreferences.id -> UpdatePreferences
            Communication.id -> Communication
            AppreciateAuthor.id -> AppreciateAuthor
            ScheduleBackup.id -> ScheduleBackup
            ImportExport.id -> ImportExport
            AiImport.id -> AiImport
            WidgetIntro.id -> WidgetIntro
            CourseReminder.id -> CourseReminder
            HolidaySettings.id -> HolidaySettings
            WebDavSettings.id -> WebDavSettings
            CourseTimeSettings.id -> CourseTimeSettings
            CourseManage.id -> CourseManage
            EducationalImport.id -> EducationalImport
            WebImport.id -> WebImport
            Feedback.id -> Feedback
            FeedbackHistory.id -> FeedbackHistory
            FeedbackRoadmap.id -> FeedbackRoadmap
            else -> null
        }
    }
}
