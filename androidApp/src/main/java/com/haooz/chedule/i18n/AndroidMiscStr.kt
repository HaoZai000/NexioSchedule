package com.haooz.chedule.i18n

/**
 * androidApp 宿主层杂项文案（导入/导出、更新、崩溃日志、Web 导入桥、
 * 文件 SAF 通道、小组件状态、主页/设置注入层）。
 *
 * 与 CommonStr / BackupStr / ImportStr 等已有命名空间语义完全一致的字面量
 * 直接复用那边，不在这里重复定义。
 */
object AndroidMiscStr {

    // ---- 课表导入（ScheduleImport / AndroidBridge）----

    val importFailed = L("导入失败", "Import failed")
    val parseFailed = L("课程数据解析失败", "Failed to parse course data")
    val parseFailedN = L("课程数据解析失败：%s", "Failed to parse course data: %s")
    val noCourseData = L("未获取到课程数据", "No course data received")
    val shareCodeInvalidHttpN = L("分享码已失效或网络异常（HTTP %d）", "Share code expired or network error (HTTP %d)")
    val starLinkInvalidResponse = L("星链课表响应格式无效", "Invalid StarLink schedule response")
    val unknownTeacher = L("未知教师", "Unknown teacher")
    val noLocation = L("未排地点", "No location")
    val wakeUpScriptDownloadFailed = L("WakeUp 适配脚本下载失败，请检查网络后重试", "Failed to download the WakeUp adapter script. Check your network and try again")
    val wakeUpScriptEmpty = L("WakeUp 适配脚本为空", "The WakeUp adapter script is empty")
    val wakeUpScriptStartFailed = L("WakeUp 脚本启动失败", "Failed to start the WakeUp script")
    val wakeUpWebViewCreateFailed = L("WakeUp WebView 创建失败", "Failed to create the WakeUp WebView")
    val clipboardShareLabel = L("Nexio课表口令", "Nexio share code")

    // ---- 课表导出（ScheduleExport）----

    val serverErrorHttpN = L("服务器错误 HTTP %d", "Server error HTTP %d")
    val serverNoCodeReturned = L("服务器未返回口令", "The server did not return a share code")
    val scheduleDataInvalid = L("课表数据无效", "Invalid schedule data")
    val sharedScheduleName = L("分享的课表", "Shared schedule")
    // 有效期用服务端返回的 expires_in 渲染（此前写死「30 分钟」，服务端一调就与实际不符）
    val shareTextNN = L(
        "我分享了课表「%s」\n口令：%s\n打开 Nexio课程表，用口令导入即可（%s 分钟内有效）",
        "I shared my schedule \"%s\"\nCode: %s\nOpen Nexio Schedule and import with this code (valid for %s minutes)"
    )
    val shareScheduleChooser = L("分享课表", "Share Schedule")
    val scheduleEmptyNoShareN = L("「%s」课表为空，无法分享", "\"%s\" is empty and cannot be shared")
    val generatingShareCode = L("正在生成分享口令…", "Generating share code…")
    val codeCopiedValidN = L("口令已复制：「%s」%s 分钟内有效", "Code copied: \"%s\". Valid for %s minutes")
    val codeCopiedImageFailedN = L("口令已复制「%s」，但生成图片失败", "Code copied: \"%s\", but generating the image failed")
    val shareFailedN = L("分享失败：%s", "Share failed: %s")
    val saveImageFailed = L("生成课表图片失败", "Failed to generate the schedule image")
    val scheduleImageSaved = L("已保存到相册", "Saved to gallery")
    val networkErrorShort = L("网络错误", "Network error")

    // ---- 应用更新（UpdateChecker / UpdateInstaller / AndroidUpdateController）----

    val logCheckDoneN = L("检查完成：channel=%s, hasUpdate=%s, remote=%s, local=%s", "Check done: channel=%s, hasUpdate=%s, remote=%s, local=%s")
    val logCheckUpdateFailed = L("检查更新失败", "Failed to check for updates")
    val logReleaseNoApkN = L("release %s 未下发 APK 资产，下载时回源重查", "release %s shipped no APK asset; re-fetching at download time")
    val logResolveUrlFoundN = L("回源解析到下载地址：%s channel=%s source=%s", "Resolved download URL: %s channel=%s source=%s")
    val logResolveUrlFailedN = L("未解析到 %s 的下载地址（缓存与回源均失败）source=%s", "No download URL resolved for %s (cache and re-fetch both failed) source=%s")
    val logSha256FailedN = L("SHA-256 计算失败：%s", "SHA-256 computation failed: %s")
    val apkIncompleteOrCorrupt = L("安装包不完整或已损坏", "The APK is incomplete or corrupted")
    val apkCentralDirDamaged = L("安装包不完整（中央目录损坏）", "The APK is incomplete (central directory damaged)")
    val logApkParseErrorN = L("解析安装包异常：%s", "Exception while parsing the APK: %s")
    val apkParseFailedN = L("安装包解析失败：%s", "Failed to parse the APK: %s")
    val apkParseFailedCorrupt = L("安装包解析失败：包体损坏", "Failed to parse the APK: package corrupted")
    val packageNameMismatchN = L("包名不匹配：%s", "Package name mismatch: %s")
    val signerMismatch = L("签名与已安装应用不一致", "Signature differs from the installed app")
    val apkVersionNotNewerNN = L("安装包版本(%d)不高于当前(%d)", "APK version (%d) is not newer than the current one (%d)")
    val digestCalcFailed = L("校验值计算失败", "Failed to compute the checksum")
    val digestMismatch = L("校验值不匹配，文件可能已损坏", "Checksum mismatch; the file may be corrupted")
    val logCleanPartN = L("清理下载中间态：%s", "Cleaned up partial download: %s")
    val logCleanCorruptApkN = L("清理损坏APK: %s 原因=%s", "Cleaned up corrupt APK: %s reason=%s")
    val logCleanedApkN = L("已清理APK: %s complete=%s keepTag=%s", "Cleaned up APK: %s complete=%s keepTag=%s")
    val logCleanOldApksFailed = L("清理旧APK失败", "Failed to clean up old APKs")
    val logLocalApkInvalidN = L("本地APK无效，已删除重下：%s 原因=%s", "Local APK invalid, deleted for re-download: %s reason=%s")
    val downloadUrlNotFound = L("未找到下载链接", "Download URL not found")
    val apkDownloadIncompleteN = L("APK 下载不完整：%s/%s", "APK download incomplete: %s/%s")
    val logNoContentLengthN = L("服务端未声明长度，改用解析级校验：%s", "Server sent no content length; falling back to parse-level verification: %s")
    val apkVerifyFailedN = L("APK 校验失败：%s", "APK verification failed: %s")
    val apkFinalizeFailed = L("APK 落盘失败", "Failed to finalize the APK file")
    val logSilentInstallFallbackN = L("静默安装失败，回退系统安装器：%s", "Silent install failed, falling back to the system installer: %s")
    val silentInstallFallback = L("静默安装失败，已改用系统安装器", "Silent install failed; switched to the system installer")
    val apkMissingReDownload = L("安装包不存在，请重新下载", "The APK file is missing; please download again")
    val installFailedN = L("安装失败：%s", "Install failed: %s")

    // ---- Shizuku（ShizukuManager）----

    val shizukuUnavailable = L("Shizuku 不可用或未授权", "Shizuku is unavailable or not authorized")
    val execError = L("执行异常", "Execution error")
    val apkNotFoundN = L("APK 不存在：%s", "APK not found: %s")
    val silentInstallSuccess = L("静默安装成功", "Silent install succeeded")

    // ---- 崩溃 / 轨迹日志（CrashLogHelper）----

    val recordingHeader = L("=== Nexio课程表 功能录制 ===", "=== Nexio Schedule feature recording ===")
    val otherCrashesHeader = L("=== 其它崩溃记录 ===", "=== Other crash records ===")
    val noneEntry = L("(无)", "(none)")
    val recordingLogSubject = L("Nexio课程表功能录制日志", "Nexio Schedule feature recording log")
    val fullContentInAttachment = L("…(完整内容见附件)", "… (full content in the attachment)")
    val shareRecordingChooser = L("分享录制日志", "Share Recording Log")
    val crashTraceLogSubject = L("Nexio课程表崩溃/轨迹日志", "Nexio Schedule crash/trace log")
    val exportCrashLogChooser = L("导出崩溃日志", "Export Crash Log")
    val exportHeader = L("=== Nexio课程表 崩溃/轨迹日志导出 ===", "=== Nexio Schedule crash/trace log export ===")
    val localSessionHeader = L("=== 本机会话 ===", "=== Local session ===")
    val traceWindowHeaderN = L("=== 功能轨迹（最近 %d 分钟） ===", "=== Feature trace (last %d min) ===")
    val logcatHeader = L("=== Logcat（release 可能受 ROM 限制） ===", "=== Logcat (may be restricted by the ROM on release builds) ===")
    val noTraceInWindowN = L("(最近 %d 分钟无功能轨迹)", "(no feature trace in the last %d min)")
    val capturedCrashesHeaderN = L("=== 已捕获崩溃 (%d) ===", "=== Captured crashes (%d) ===")
    val noCrashRecords = L("(尚无 Java/Kotlin 崩溃记录。若为原生崩溃，请附上 logcat / 功能轨迹。)", "(no Java/Kotlin crash records yet. For native crashes, attach logcat / feature trace.)")
    val moreCrashesN = L("…另有 %d 条未展开", "…%d more not shown")
    val crashReportHeader = L("=== Nexio课程表 崩溃报告 ===", "=== Nexio Schedule crash report ===")

    // ---- Web 教务导入（AndroidWebHost / AndroidBridge / WebCompatDelegate / WebViewRequestInterceptor / WebForwardHosts）----

    val logParseSelectionFailedN = L("解析单选列表失败：%s", "Failed to parse the selection list: %s")
    val selectionDataError = L("选项数据错误", "Invalid option data")
    val selectionJsonInvalidN = L("选项列表 JSON 无效：%s", "Invalid option list JSON: %s")
    val logReceivedCoursesN = L("接收到课程数据，大小：%d KB", "Received course data, size: %d KB")
    val courseImportSuccess = L("课程导入成功", "Courses imported")
    val configImportSuccess = L("课表配置导入成功", "Schedule config imported")
    val timeSlotsImportSuccess = L("时间段导入成功", "Time slots imported")
    val comboImportSuccess = L("组合作息导入成功", "Combo routine imported")
    val logMethodRejectedN = L("%s 被拒绝：%s", "%s rejected: %s")
    val logMethodExceptionN = L("%s 处理异常", "%s failed with an exception")
    val reloadSiteDifferently = L("正在换一种方式重新加载该站点…", "Reloading this site a different way…")
    val logSslProceedN = L("SSL 错误已放行：%s primaryError=%s", "SSL error proceeded: %s primaryError=%s")
    val loadFailedN = L("加载失败：%s", "Load failed: %s")
    val logSubresourceFailedN = L("子资源加载失败[%d]: %s —— %s", "Subresource load failed [%d]: %s — %s")
    val logNoDocStart = L("WebView 不支持 document-start 脚本，回退到 onPageFinished 注入", "WebView does not support document-start scripts; falling back to onPageFinished injection")
    val logViewportInjectFailed = L("document-start 视口注入失败，回退到 onPageFinished 注入", "document-start viewport injection failed; falling back to onPageFinished injection")
    val logForwardN = L("转发 %s dest=%s %s", "Forward %s dest=%s %s")
    val logForwardResultN = L("转发结果 %s %s %sB %s", "Forward result %s %s %sB %s")
    val logForwardEnabledN = L("已对该站点启用请求转发（剥离 X-Requested-With）：%s", "Request forwarding enabled for this site (strips X-Requested-With): %s")

    // ---- 文件导入导出 / 备份 SAF 通道（AndroidScheduleFileController / AndroidBackupFileController）----

    val cannotReadSelectedFile = L("无法读取所选文件", "Cannot read the selected file")
    val cannotOpenFilePicker = L("无法唤起文件选择器", "Cannot open the file picker")
    val cannotWriteSelectedLocation = L("无法写入所选位置", "Cannot write to the selected location")
    val exportSuccess = L("导出成功", "Export successful")
    val exportFailedN = L("导出失败：%s", "Export failed: %s")
    val backupDirNotWritableN = L("备份目录不可写：%s", "Backup directory is not writable: %s")
    val unknownTime = L("未知时间", "Unknown time")
    val backupOverSizeLimit = L("备份文件超过8 MB限制", "Backup file exceeds the 8 MB limit")

    // ---- 小组件状态（TodayCoursesProvider）----

    val holidayNoCourses = L("假期中，暂无课程", "On break, no classes")
    val semesterNotStartedYet = L("学期暂未开始", "Semester not started yet")
    val noCoursesTomorrow = L("明日无课", "No classes tomorrow")
    val noCoursesToday = L("今日无课", "No classes today")
    val todayCoursesFinished = L("今日课程已上完", "Today's classes are over")
    val minutesToEndN = L("%d分钟结束", "Ends in %d min")

    // ---- 主页 / 设置注入层（MainActivity / AndroidSettingsScreen / AppRouteContent）----

    val noImageSelected = L("未选择图片", "No image selected")
    val processingWallpaperWait = L("正在处理壁纸，请稍候", "Processing wallpaper, please wait")
    val processingWallpaper = L("正在处理壁纸…", "Processing wallpaper…")
    val wallpaperDecodeFailed = L("壁纸解码失败，请换一张图片", "Failed to decode the wallpaper; try another image")
    val aiImportClipLabel = L("课表导入提示词", "Schedule import prompt")
    val islandTestSent = L("已发送超级岛测试通知", "Super Island test notification sent")
    val liveActivityTestSent = L("已发送实时活动测试通知", "Live Activity test notification sent")
}
