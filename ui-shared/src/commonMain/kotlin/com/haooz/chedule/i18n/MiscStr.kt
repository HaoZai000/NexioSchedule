package com.haooz.chedule.i18n

/**
 * 杂项工具与页面的文案：分享编解码（ShareScheduleCodec）、玻璃材质档位（AppMaterialSettings）、
 * 隐私同意弹窗（PrivacyConsentScreen）、第三方口令导入（ThirdPartyShare）、WebDAV 设置页。
 */
object MiscStr {

    // ---- 分享课表编解码校验（IllegalArgumentException 消息，经 Toast 展示） ----

    val shareStartWeekInvalid = L("分享课表的startWeek无效", "Invalid startWeek in shared schedule")
    val shareEndWeekInvalid = L("分享课表的endWeek无效", "Invalid endWeek in shared schedule")
    val shareWeekTypeInvalid = L("分享课表的weekType无效", "Invalid weekType in shared schedule")
    val shareWeekRangeInvalid = L("分享课表的课程周次范围无效", "Invalid course week range in shared schedule")
    val shareOddEvenTypeInvalid = L("分享课表的课程单双周类型无效", "Invalid odd/even week type in shared schedule")
    val shareSemesterWeeksInvalid = L("分享课表的学期总周数无效", "Invalid semester total weeks in shared schedule")
    val shareWeeksOutOfRange = L("分享课表的课程周次超出支持范围", "Course weeks exceed the supported range")
    val shareSelectedWeeksInvalid = L("分享课表的selectedWeeks无效", "Invalid selectedWeeks in shared schedule")
    val shareSelectedWeeksDuplicate = L("分享课表的selectedWeeks重复", "Duplicate weeks in selectedWeeks")
    val shareSelectedWeeksOutOfRange = L("分享课表的selectedWeeks超出支持范围", "selectedWeeks exceed the supported range")
    val shareSettingsInvalid = L("分享课表的设置数据无效", "Invalid settings data in shared schedule")
    val shareSettingsFieldsInvalid = L("分享课表的设置字段无效", "Invalid settings fields in shared schedule")
    val shareTotalWeeksInvalid = L("分享课表的总周数无效", "Invalid total weeks in shared schedule")
    val shareMissingCourses = L("分享课表缺少课程数据", "Missing course data in shared schedule")
    val shareNoCourses = L("分享课表没有课程", "The shared schedule has no courses")
    val shareCourseInvalid = L("分享课表中的课程数据无效", "Invalid course data in shared schedule")
    val shareCourseFieldsInvalid = L("分享课表中的课程字段无效", "Invalid course fields in shared schedule")
    val shareCourseNameMissing = L("分享课表中的课程缺少有效名称", "A course in the shared schedule has no valid name")
    val shareDayInvalid = L("分享课表中的星期无效", "Invalid day of week in shared schedule")
    val shareStartSectionInvalid = L("分享课表中的开始节次无效", "Invalid start section in shared schedule")
    val shareEndSectionInvalid = L("分享课表中的结束节次无效", "Invalid end section in shared schedule")
    val shareSectionRangeInvalid = L("分享课表中的节次范围无效", "Invalid section range in shared schedule")
    val shareStartTimeInvalid = L("分享课表中的开始时间无效", "Invalid start time in shared schedule")
    val shareEndTimeInvalid = L("分享课表中的结束时间无效", "Invalid end time in shared schedule")
    val shareTimeRangeInvalid = L("分享课表中的时间范围无效", "Invalid time range in shared schedule")

    // ---- 玻璃材质档位（AppMaterialSettings，档位常量本身是持久化数据键） ----

    val materialBest = L("柔光玻璃", "Soft Glass")
    val materialBalanced = L("清透磨砂", "Frosted Glass")
    val materialNone = L("无材质", "No Material")

    // ---- 隐私政策同意弹窗 ----

    val privacyPolicyTitle = L("隐私政策", "Privacy Policy")
    val privacyConsentSummary = L(
        "欢迎使用 Nexio 课程表。\n\n为提供课程表、课程提醒、天气等功能，我们需要在您同意后申请必要权限，并处理实现功能所必需的信息。\n您的课程数据默认仅保存在本机，设备信息仅用于匿名统计且不含身份信息。",
        "Welcome to Nexio Schedule.\n\nTo provide the schedule, course reminders, weather and other features, we need your consent to request the necessary permissions and to process the information required to implement them.\nYour course data is stored on this device only by default; device information is used for anonymous statistics only and contains no identity information.",
    )
    val privacyPolicyLink = L("《Nexio 课程表隐私政策》", "Nexio Schedule Privacy Policy")
    val agreeAndContinue = L("同意并继续", "Agree & Continue")
    val disagree = L("不同意", "Disagree")

    // ---- 第三方口令导入（WakeUp / 星链） ----

    val wakeUpImportTitle = L("WakeUp课程表口令导入", "WakeUp Share Code Import")
    val starLinkImportTitle = L("星链课表分享码导入", "StarLink Share Code Import")
    val wakeUpInputLabel = L("分享口令", "Share Code")
    val starLinkInputLabel = L("分享码", "Share Code")

    // ---- WebDAV 设置页 ----

    val lastOperationN = L("上次操作：%s", "Last operation: %s")
    val backingUpStatus = L("正在备份…", "Backing up…")
    val restoringStatus = L("正在恢复…", "Restoring…")
    val processingStatus = L("处理中…", "Processing…")
    val backupSuccessN = L("备份成功：%s", "Backup successful: %s")
    val restoreSuccessN = L("恢复成功：%s", "Restore successful: %s")
    val connectionFailed = L("连接失败", "Connection failed")
    val serverConfigSection = L("服务器配置", "Server Configuration")
    val serverAddress = L("服务器地址", "Server address")
    val backupPath = L("备份路径", "Backup path")
    val accountLabel = L("用户名", "Account")
    val appPasswordLabel = L("应用密码", "App password")
    val hintLeaveEmptyForRoot = L("留空=根目录", "Leave empty for root")
    val requiredHint = L("必填", "Required")
    val configureServerFirst = L("请先配置服务器信息", "Configure the server info first")
    val noOperationRecord = L("暂无操作记录", "No operations yet")
    val cloudBackupAfterConfig = L("配置后即可使用云同步功能", "Cloud sync is available after configuration")
    val neverOperated = L("从未操作", "Never used")
    val nutstoreStepsTitle = L("关联坚果云步骤：", "Link Nutstore (Jianguoyun):")
    val nutstoreStepsDetail = L(
        "① 下载并安装坚果云客户端\n② 登录后前往「设置 → 第三方应用管理」获取应用密码\n③ 将应用密码填入上方「应用密码」",
        "① Download and install the Nutstore client\n② Sign in and go to \"Settings → Third-party App Management\" to get an app password\n③ Enter the app password in \"App password\" above",
    )
    val backupRestoreNotesTitle = L("备份与恢复说明", "Backup and Restore Notes")
    val backupRestoreNotesDetail = L(
        "同步到云端：将全部课表数据上传为带时间戳的文件，每次备份独立保存，不覆盖历史备份。\n\n从云端恢复：下载最新的备份文件覆盖本地数据。恢复前请先备份当前数据。",
        "Sync to cloud: all schedule data is uploaded as timestamped files. Each backup is saved independently and never overwrites previous ones.\n\nRestore from cloud: the latest backup file is downloaded and overwrites local data. Back up your current data before restoring.",
    )
}
