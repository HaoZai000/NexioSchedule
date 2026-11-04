package com.haooz.chedule.i18n

/**
 * 数据管理（备份 / 恢复 / 导入 / 导出）页面文案。
 * 术语统一：备份=Backup、恢复=Restore、迁移=Migration。
 */
object BackupStr {

    // ---- 通用 / 状态 ----

    val fileReadFailed = L("文件读取失败", "Failed to read file")
    val notSelected = L("未选择", "Not selected")
    val externalFile = L("外部文件", "External file")
    val importing = L("导入中…", "Importing…")
    val sharing = L("分享中…", "Sharing…")
    val backingUp = L("备份中…", "Backing up…")
    val scanningBackups = L("正在扫描备份文件…", "Scanning backup files…")

    // ---- 结果 / 错误 ----

    val backupSuccessN = L("备份成功：%s", "Backup successful: %s")
    val backupFailedN = L("备份失败：%s", "Backup failed: %s")
    val backupWriteFailed = L("备份文件写入失败", "Failed to write backup file")
    val restoreSuccess = L("恢复成功", "Restored")
    val restoreFailedN = L("恢复失败：%s", "Restore failed: %s")
    val deleteFailedN = L("删除失败：%s", "Delete failed: %s")
    val deleteFailedNotWritable = L("删除失败：文件不存在或不可写", "Delete failed: file missing or not writable")
    val noRestorableFile = L("没有可恢复的文件", "No file to restore")
    val formatError = L("格式错误", "Invalid format")
    val unrecognizedBackupN = L("无法识别备份文件：%s", "Unrecognized backup file: %s")
    val platformNotSupportedShareCodeImport = L("当前平台不支持口令导入", "Share code import is not supported on this platform")
    val platformNotSupportedN = L("当前平台不支持%s", "%s is not supported on this platform")
    val shareCodeNotFound = L("口令不存在或已过期", "Share code not found or expired")
    val thirdPartyCodeInvalid = L("口令无效或导入失败", "Invalid code or import failed")
    val enterShareCode = L("请输入口令", "Enter a share code")
    val enterInputN = L("请输入%s", "Enter %s")
    val selectScheduleToBackupFirst = L("请先选择要备份的课表", "Select a schedule to back up first")
    val selectScheduleToShareFirst = L("请先选择要分享的课表", "Select a schedule to share first")

    // ---- 备份 / 恢复 ----

    val backupSection = L("备份", "Backup")
    val backupModeLabel = L("备份模式", "Backup Mode")
    val backupNow = L("立即备份", "Back Up Now")
    val fullBackup = L("全部备份", "Full Backup")
    val singleSchedule = L("单独课表", "Single Schedule")
    val backupAllSummary = L("备份全部课表数据至存储", "Back up all schedule data to storage")
    val backupSingleSummaryN = L("备份「%s」至存储", "Back up \"%s\" to storage")
    val backupHistorySection = L("备份历史", "Backup History")
    val scheduleCountN = L("共%d个课表", "%d schedules")
    val selectScheduleToBackup = L("请选择要备份的课表", "Select a schedule to back up")
    val fullBackupNoSelection = L("全部备份模式下无需选择", "No selection needed for full backup")
    val localBackup = L("本地备份", "Local Backup")
    val localBackupSummary = L("备份课表数据到设备存储", "Back up schedule data to device storage")
    val webDavBackup = L("WebDAV 云备份", "WebDAV Cloud Backup")
    val webDavBackupSummary = L("配置WebDAV后可云备份/恢复", "Configure WebDAV for cloud backup and restore")
    val lastOperationAtN = L("上次操作：%s", "Last operation: %s")
    val noOperationYet = L("未操作", "No operations yet")
    val restore = L("恢复", "Restore")
    val restoreBackup = L("恢复备份", "Restore Backup")
    val restoreFullBackup = L("恢复全部备份", "Restore Full Backup")
    val overwriteRestore = L("覆盖恢复", "Overwrite & Restore")
    val confirmRestore = L("确定恢复", "Restore")
    val restoreFullSummaryN = L(
        "「%s」\n这是全量备份，包含 %d 个课表。\n恢复会先清除现有全部课表与时间配置，再整体覆盖为备份内容，此操作不可撤销。",
        "\"%s\"\nThis is a full backup containing %d schedules.\nRestoring will first clear all existing schedules and time settings, " +
            "then overwrite everything with the backup content. This action cannot be undone.",
    )
    val restoreSingleSummaryN = L(
        "「%s」\n这是单课表备份，包含 %d 门课程。\n将新建课表「%s」（重名时自动编号），不影响现有课表。",
        "\"%s\"\nThis is a single-schedule backup containing %d courses.\n" +
            "A new schedule \"%s\" will be created (auto-numbered if the name already exists); existing schedules are not affected.",
    )
    val deleteBackup = L("删除备份", "Delete Backup")
    val deleteBackupSummaryN = L("确定要删除备份「%s」吗？\n此操作不可恢复", "Delete backup \"%s\"?\nThis cannot be undone")
    val otherOperations = L("其他操作", "Other Actions")
    val manualFilePick = L("手动选择文件", "Select File Manually")
    val manualFilePickSummary = L("从其他位置选择备份文件进行恢复", "Pick a backup file from another location to restore")

    // ---- 导出 ----

    val exportSchedule = L("导出课表", "Export Schedule")
    val selectSchedule = L("选择课表", "Select Schedule")
    val noScheduleForExport = L("暂无课表，请先创建或导入后再导出", "No schedules yet. Create or import one before exporting")
    val exportFormat = L("导出格式", "Export Format")
    val jsonExport = L("JSON 格式导出", "Export as JSON")
    val jsonExportSummary = L("导出课表为JSON格式", "Export the schedule as a JSON file")
    val icsExport = L("ICS 格式导出", "Export as ICS")
    val icsExportSummary = L("导出课表为日程格式", "Export the schedule as a calendar file")
    val shareCodeExport = L("口令分享导出", "Share Code Export")
    val shareCodeExportSummary = L("生成趣味口令与分享图片", "Generate a fun share code and image")
    val shareExportConfirmSummaryN = L(
        "将课表「%s」上传生成分享口令？\n口令有效期有限，过期后可重新生成",
        "Upload schedule \"%s\" to generate a share code?\nThe code expires after a while; you can generate a new one anytime",
    )
    val confirmShare = L("确认分享", "Share")
    val exportToWearable = L("导出到手环", "Export to Wearable")
    val exportToWearableSummary = L("打包JSON并推送到手表/手环", "Package as JSON and push to a watch or band")
    val wearableExportSuccessN = L("已导出并推送手环\n%s", "Exported and pushed to wearable\n%s")
    val wearableExportFailed = L("导出失败，请确认手环已连接", "Export failed. Make sure the wearable is connected")
    val exportEmptyScheduleN = L("「%s」课表为空，无法导出", "Schedule \"%s\" is empty and cannot be exported")

    // ---- 同步到日历 ----

    val syncToCalendar = L("同步到日历", "Sync to Calendar")
    val syncToCalendarSummary = L("把课表逐节课写进系统日历", "Write every class into the system calendar")
    val calendarSyncSuccessN = L("已同步 %d 节课到日历", "Synced %d classes to the calendar")
    val calendarPermissionDenied = L("未获得日历权限，无法同步", "Calendar permission is required to sync")
    val calendarNoWritable = L(
        "没有可写入的日历，请先在系统日历中添加账户",
        "No writable calendar. Add a calendar account first",
    )
    val calendarSyncFailed = L("同步到日历失败", "Failed to sync to the calendar")

    // ---- 导入 ----

    val importMethods = L("导入方式", "Import Methods")
    val importSchedule = L("导入课表", "Import Schedule")
    val importScheduleConfirmSummaryN = L(
        "是否导入课表「%s」？\n确定导入将创建一个新的课表",
        "Import schedule \"%s\"?\nConfirming will create a new schedule",
    )
    val importCreatesNewSchedule = L("确定导入将创建一个新的课表", "Confirming will create a new schedule")
    val confirmImport = L("确定导入", "Import")
    /** 导入弹窗内撞名时的说明：说清「谁被覆盖」，覆盖是不可逆的 */
    val importOverwriteSummaryN = L(
        "已存在同名课表「%s」\n确定导入将覆盖它的课程与作息",
        "A schedule named \"%s\" already exists\nImporting will overwrite its courses and class times",
    )
    val overwriteImport = L("覆盖导入", "Overwrite")
    val fetchSchedule = L("获取课表", "Fetch Schedule")
    val scheduleNameLabel = L("课表名称", "Schedule Name")
    val importedScheduleName = L("导入的课表", "Imported schedule")
    val icsImportedScheduleName = L("ICS导入课表", "Imported from ICS")
    val wakeUpImportedName = L("WakeUp导入课表", "Imported from WakeUp")
    val starLinkImportedName = L("星链导入课表", "Imported from StarLink")
    val shareCodeSection = L("口令", "Share Code")
    val fileSection = L("文件", "Files")
    val fileAndCodeSection = L("文件与口令", "Files & Share Codes")
    val shareCodeImport = L("分享口令导入", "Share Code Import")
    val shareCodeImportSummary = L("输入好友分享的口令导入课表", "Enter a share code from a friend to import a schedule")
    val shareCodeImportTitle = L("口令导入", "Share Code Import")
    val shareCodeDialogSummary = L("输入好友分享的口令，过期后需重新获取", "Enter a share code from a friend. Ask for a new one if it has expired")
    val shareCodeLabel = L("分享口令", "Share Code")
    val wakeUpCodeImport = L("WakeUp课程表口令导入", "WakeUp Share Code Import")
    val wakeUpCodeImportSummary = L("输入WakeUp分享口令即可获取", "Enter a WakeUp share code to fetch the schedule")
    val starLinkCodeImport = L("星链课表分享码导入", "StarLink Share Code Import")
    val starLinkCodeImportSummary = L("输入星链课表分享码即可获取", "Enter a StarLink share code to fetch the schedule")
    val thirdPartyCodeSummaryN = L("输入%s即可导入课表", "Enter %s to import the schedule")
    val thirdPartyConfirmSummaryN = L(
        "是否导入「%s」课表？\n共 %d 门课程，将创建新课表",
        "Import schedule \"%s\"?\n%d courses in total; a new schedule will be created",
    )
    val aiTextImport = L("AI 文本导入", "AI Text Import")
    val aiTextImportSummary = L("粘贴由AI解析后的课程进行导入", "Paste AI-parsed courses to import")
    val educationalImport = L("教务系统导入", "Academic System Import")
    val educationalImportSummary = L("从学校教务系统一键拉取课表", "Fetch your schedule from the school system in one tap")
    val jsonFileImport = L("JSON 文件导入", "Import JSON File")
    val jsonFileImportSummary = L("支持拾光课程表/Nexio课程表", "Supports Shiguang Schedule/Nexio Schedule")
    val icsFileImport = L("ICS 文件导入", "Import ICS File")
    val icsFileImportSummary = L("从日程文件导入课程", "Import courses from a calendar file")
    val noScheduleData = L("暂无课表数据", "No schedule data")
    val noBackupRecords = L("暂无备份记录", "No backup records yet")
}
