package com.haooz.chedule.i18n

/**
 * 导入相关页面文案：AI 文本导入、教务导入（学校 / 适配器选择、网页导入）与第三方导入落库。
 *
 * 节次配置行前缀（"上午节数" 等与 AI 输出格式约定的协议词）、JSON / prefs 键、
 * 学校名与适配器脚本名属于数据键，保留在调用方，不进本命名空间。
 */
object ImportStr {

    // ── AI 文本导入：页面结构 ──
    val scheduleData = L("课表数据", "Schedule data")
    val pasteAiDataHint = L("请粘贴AI返回的课表数据", "Paste the AI-returned schedule data")
    val parsePreview = L("解析预览", "Parse preview")
    val identifiedCoursesN = L("已识别 %d 门课程", "Identified %d courses")
    val sectionConfigIdentified =
        L("已识别节次配置：上午%d节 · 下午%d节 · 晚上%d节", "Section config identified: %d morning · %d afternoon · %d evening periods")
    val withTimesSuffix = L("（含上课时间，导入时自动应用）", " (with class times, applied automatically on import)")
    val autoApplySuffix = L("（导入时自动应用）", " (applied automatically on import)")
    val warningsCountN = L("提示 %d 条", "%d tips")
    val warningDetails = L("提示详情", "Details")
    val usageGuide = L("使用说明", "Instructions")
    val copyPrompt = L("复制提示词", "Copy prompt")
    val promptCopied = L("提示词已复制到剪切板", "Prompt copied to clipboard")
    val promptUsageHint = L(
        "请将以下提示词发送给 AI ，再将 AI 返回的文本粘贴到上方输入框。",
        "Send the prompt below to the AI, then paste its response into the input box above."
    )

    // ── AI 文本导入：提示词（英文版约定同一格式，键名换英文，解析侧同步识别） ──
    val aiPrompt = L(
        """请根据课表信息按以下格式返回，包含节次配置和课程列表两部分：

第一部分 - 节次配置（放在课程列表之前，时间行可选，无时间则不输出）：
上午节数=N
下午节数=N
晚上节数=N
上午时间=1:08:00-08:45,2:08:55-09:40
下午时间=1:14:00-14:45,2:14:55-15:40
晚上时间=1:19:00-19:45,2:19:55-20:40

第二部分 - 课程列表（每行一门课程，用"|"分隔）：
课程名称|教室|教师|星期几|开始节次|结束节次|周次

字段说明：
- 星期几：1=周一 ... 7=周日
- 周次：英文逗号分隔，连续周次展开，例：1-6周和10-12周写为 1,2,3,4,5,6,10,11,12
- 教室和教师无法识别则留空

重要：同名但教师或教室不同的课程必须分行输出，只有名称、教室、教师、星期、节次完全相同时才可合并周次。

示例：
上午节数=4
下午节数=4
晚上节数=2
上午时间=1:08:00-08:45,2:08:55-09:40,3:10:00-10:45,4:10:55-11:40
下午时间=1:14:00-14:45,2:14:55-15:40,3:16:00-16:45,4:16:55-17:40
晚上时间=1:19:00-19:45,2:19:55-20:40
高等数学|A101|张三|1|1|2|1,2,3,4,5,6,7,8,9,10
高等数学|B202|李四|3|3|4|1,2,3,4,5,6,7,8,9,10
大学英语|||3|3|4|1,2,3,4,5,6,7,8

请严格按此格式返回，不要添加其他说明文字。""",
        """Based on the schedule information below, reply in the following format, which has two parts: section configuration and the course list.

Part 1 - Section configuration (before the course list; time lines are optional, omit them if unknown):
Morning sections=N
Afternoon sections=N
Evening sections=N
Morning times=1:08:00-08:45,2:08:55-09:40
Afternoon times=1:14:00-14:45,2:14:55-15:40
Evening times=1:19:00-19:45,2:19:55-20:40

Part 2 - Course list (one course per line, fields separated by "|"):
Course name|Classroom|Teacher|Weekday|Start period|End period|Weeks

Field notes:
- Weekday: 1=Monday ... 7=Sunday
- Weeks: comma-separated, expand consecutive ranges, e.g. weeks 1-6 and 10-12 are written as 1,2,3,4,5,6,10,11,12
- Leave classroom and teacher empty if unrecognized

Important: courses with the same name but a different teacher or classroom must be output on separate lines; weeks may be merged only when name, classroom, teacher, weekday and periods are all identical.

Example:
Morning sections=4
Afternoon sections=4
Evening sections=2
Morning times=1:08:00-08:45,2:08:55-09:40,3:10:00-10:45,4:10:55-11:40
Afternoon times=1:14:00-14:45,2:14:55-15:40,3:16:00-16:45,4:16:55-17:40
Evening times=1:19:00-19:45,2:19:55-20:40
Advanced Mathematics|A101|John|1|1|2|1,2,3,4,5,6,7,8,9,10
Advanced Mathematics|B202|Jane|3|3|4|1,2,3,4,5,6,7,8,9,10
College English|||3|3|4|1,2,3,4,5,6,7,8

Reply strictly in this format and do not add any other text."""
    )

    // ── AI 文本导入：确认导入弹窗 ──
    val appendDialogTitle = L("增量添加到当前课表？", "Append to the current schedule?")
    val overwriteDialogTitle = L("导入并覆盖当前课表？", "Import and overwrite the current schedule?")
    val appendSummaryCombined = L("当前课表 %d 门 + 新增 %d 门 → 共 %d 门", "Current %d + new %d → %d in total")
    val appendKeepExisting = L("\n保留现有课程，不覆盖", "\nExisting courses are kept, nothing is overwritten")
    val totalCoursesN = L("共 %d 门课程", "%d courses in total")
    val replaceSummary = L("当前课表 %d 门 → 新课表 %d 门", "Current %d courses → new %d courses")
    val applySectionConfigInfo =
        L("\n将同时应用节次配置：\n上午%d节 · 下午%d节 · 晚上%d节", "\nSection config will also be applied:\n%d morning · %d afternoon · %d evening periods")
    val appendModeLabel = L("增量添加", "Append")
    val appendModeOnDesc = L("保留现有课程，追加到当前课表", "Keep existing courses and append to the current schedule")
    val appendModeOffDesc = L("关闭则清空现有课表后导入", "When off, the current schedule is cleared before importing")
    val confirmAppend = L("确认添加", "Append")
    val confirmImport = L("确认导入", "Import")
    val confirmImportCard = L("确定导入", "Import")
    val addedCoursesN = L("成功添加 %d 门课程", "Added %d courses")
    val importedCoursesN = L("成功导入 %d 门课程", "Imported %d courses")

    // ── AI 文本导入：课程预览行 ──
    val sectionRangeSuffix = L("｜第%d-%d节", " · Periods %d-%d")
    val classroomSuffix = L("｜%s", " · %s")

    // ── AI 文本导入：解析（非 Composable，调用时刻取值） ──
    val unnamedCourse = L("未命名课程", "Unnamed course")
    val lineInsufficientFields = L("第%d行：字段数量不足，已跳过", "Line %d: not enough fields, skipped")
    val lineInvalidDay = L("第%d行「%s」：星期几无效，已跳过", "Line %d \"%s\": invalid weekday, skipped")
    val lineInvalidStartSection =
        L("第%d行「%s」：开始节次无效（应为 1-%d），已跳过", "Line %d \"%s\": invalid start section (expected 1-%d), skipped")
    val lineInvalidEndSection =
        L("第%d行「%s」：结束节次无效（应为 %d-%d），已跳过", "Line %d \"%s\": invalid end section (expected %d-%d), skipped")
    val lineInvalidWeeks = L("第%d行「%s」：周次无效，已跳过", "Line %d \"%s\": invalid weeks, skipped")
    val lineParseError = L("第%d行：格式解析异常，已跳过", "Line %d: failed to parse, skipped")

    // ── 教务导入：选择页 ──
    val tabSchoolImport = L("学校导入", "School import")
    val generalTools = L("通用工具", "General tools")
    val selectSchool = L("选择学校", "Choose school")
    val searchSchool = L("搜索学校", "Search schools")

    // ── 教务导入：脚本更新 ──
    val alreadyLatest = L("已是最新版本", "Already up to date")
    val updateComplete = L("更新完成", "Update complete")
    val updateFailedNoNetwork = L("更新失败，请检查网络连接", "Update failed, check your network connection")
    val updateFailed = L("更新失败", "Update failed")

    // ── 教务导入：后台日志 ──
    val logApplyPresetFailed = L("应用预设时间段失败：%s", "Failed to apply preset time slots: %s")
    val logApplyStartDate = L("应用开学日：raw=%s -> %s target=%s", "Applied semester start date: raw=%s -> %s target=%s")
    val logStartDateIgnored = L("开学日无法解析，已忽略：%s", "Semester start date unparseable, ignored: %s")
    val logApplyDurations = L("应用默认节次时长：class=%d break=%d target=%s", "Applied default period durations: class=%d break=%d target=%s")
    val logNormalizeWeekdays =
        L("按周起始日归一化星期：firstDayOfWeek=%d target=%s", "Normalized weekdays by first day of week: firstDayOfWeek=%d target=%s")
    val logApplyConfigSuccess =
        L("应用课表配置成功：开学时间=%s, 总周数=%d, target=%s", "Applied schedule config: startDate=%s, totalWeeks=%d, target=%s")
    val logApplyComboSuccess = L("应用组合作息成功：%d 个方案 target=%s", "Applied combo routines: %d plan(s), target=%s")
    val logApplyComboFailed = L("应用组合作息失败：%s", "Failed to apply combo routines: %s")
    val logUpdateFailed = L("更新失败：%s", "Update failed: %s")
    val logManualUpdateFailed = L("手动更新失败：%s", "Manual update failed: %s")
    val defaultRoutineName = L("作息方案", "Routine")

    // ── 网页导入 ──
    val refresh = L("刷新", "Refresh")
    val coursesSavedN = L("课程已保存，共 %d 门课程", "Courses saved, %d in total")

    // ── 适配器选择 ──
    val noAdapters = L("该学校暂无适配器", "No adapters available for this school yet")
    val categoryUndergraduate = L("本科", "Undergraduate")
    val categoryPostgraduate = L("研究生", "Postgraduate")
    val enterUrl = L("输入网址", "Enter URL")
    val enterUrlHint = L("请输入要访问的教务系统网址", "Enter the academic system URL to visit")
    val urlLabel = L("网址", "URL")
    val noDescription = L("暂无详细描述", "No description yet")
    val maintainerN = L("维护者：%s", "Maintainer: %s")

    // ── 学校选择 ──
    val noSchoolData = L("暂无学校数据", "No school data available")
    val noSchoolMatch = L("未找到匹配的学校", "No matching schools found")
    val checkingUpdates = L("正在检查更新", "Checking for updates")

    // ── 导入落库（第三方分享 / WakeUp 等） ──
    val scheduleExists = L("课表「%s」已存在", "Schedule \"%s\" already exists")
    val reorgNeedsStartDate =
        L("包含教学周重组的分享课表必须有有效开学日期", "A shared schedule with teaching-week reorganizations must have a valid semester start date")
    val coursesIncomplete = L("分享课表中的课程数据无法完整导入", "Not all courses in the shared schedule could be imported")
    val reorgWeeksConflict = L("教学周重组规则与课表总周数冲突", "Teaching-week reorganization rules conflict with the schedule's total weeks")
    val importSuccess = L("成功导入课表「%s」\n共 %d 门课程", "Imported schedule \"%s\"\n%d courses in total")
    val importSuccessCompact = L("成功导入课表「%s」\n共%d门课程", "Imported schedule \"%s\"\n%d courses in total")
    /** 用户确认覆盖同名课表后的成功提示：必须点明「覆盖」，否则与新建的提示分不清谁被改了 */
    val importOverwritten = L("已覆盖课表「%s」\n共 %d 门课程", "Overwrote schedule \"%s\"\n%d courses in total")
    val importOverwrittenCompact = L("已覆盖课表「%s」\n共%d门课程", "Overwrote schedule \"%s\"\n%d courses in total")
    val importFailedReason = L("导入失败：%s", "Import failed: %s")
    val noCoursesParsed = L("未解析到课程数据", "No course data parsed")

    // ── 教务导入桥（注入 WebView 的桥接消息；方法名 / JSON 字段名是协议键，留在调用方） ──
    val bridgeParseCoursesFailed = L("课程数据解析失败：%s", "Failed to parse course data: %s")
    val bridgeCoursesEmpty = L("课程数据为空", "Course data is empty")
    val bridgeInvalidDayWarningN = L(
        "%d 门课程 day 不在 1..7，导入后可能不显示",
        "%d courses have a day outside 1..7 and may not show after import",
    )
    val bridgeParseConfigFailed = L("课表配置解析失败：%s", "Failed to parse schedule config: %s")
    val bridgeConfigLog = L("课表配置：start=%s weeks=%d firstDay=%d", "Schedule config: start=%s weeks=%d firstDay=%d")
    val bridgeComboAlreadySaved = L(
        "组合作息已提交，本次导入不再接受基础时间段",
        "Combo routines already submitted; base time slots are no longer accepted in this import",
    )
    val bridgeParseTimeSlotsFailed = L("时间段数据解析失败：%s", "Failed to parse time slot data: %s")
    val bridgeTimeSlotsEmpty = L("时间段数据为空", "Time slot data is empty")
    val bridgeCallSaveTimeSlotsFirst = L(
        "请先调用 savePresetTimeSlots 提交基础时间段",
        "Call savePresetTimeSlots first to submit the base time slots",
    )
    val bridgeParseComboFailed = L("组合作息解析失败：%s", "Failed to parse combo routines: %s")
    val bridgeComboNoPublicSchedules = L("组合作息没有 publicSchedules", "Combo routines have no publicSchedules")

    // ── AI 教务导入：模型自己读页面 / 打接口 / 写脚本 ──
    val aiEduImportTitle = L("AI 教务导入", "AI academic import")
    val aiEduStart = L("开始分析", "Analyze")
    /** 收起对话去网页里操作后，从底栏回到同一段对话（不是重新开始） */
    val aiEduResumeChat = L("继续对话", "Resume chat")
    /** 输入地址命中本地适配时，底部弹窗里继续走 AI 的入口 */
    val aiEduContinue = L("继续AI教务导入", "Continue with AI import")
    val aiEduResumeHint = L("已收起对话，点这里继续", "Chat hidden, tap to resume")
    val aiEduStop = L("停止", "Stop")
    val aiEduFailed = L("这次没成", "Didn't work this time")
    val aiEduParseFailed = L("课程数据解析失败", "Failed to parse course data")
    val aiEduReplyHint = L("补充缺失的信息", "Add the missing information")
    val aiEduSend = L("发送", "Send")
    val aiEduNeedUrl = L("先填一个教务系统地址", "Enter your academic system address first")
    val aiEduUrlHint = L(
        "填写学校教务导入地址后打开教务系统，登录教务系统后进入课表页面再点击开始分析\n" +
            "AI自动开始分析页面与接口，撰写脚本",
        "Enter your school's academic system address and open it, sign in, go to the timetable page, " +
            "then tap Analyze.\nAI then reads the page and APIs and writes the adapter script.",
    )
    val aiEduUrlLabel = L("教务系统地址", "Academic system address")
    val aiEduOpen = L("打开", "Open")
    val aiEduEntrySubtitle = L(
        "AI自动开始分析页面与接口，撰写脚本",
        "AI reads the page and APIs and writes the adapter script",
    )

    // ── AI 教务导入：三个阶段（网页 / 对话 / 预览） ──
    /** 底栏说明：告诉用户分析前要先把课表页翻出来 */
    val aiEduDockHint = L("请先打开课表页面再分析", "Open your timetable page first, then analyze")

    val aiEduStatusRunning = L("思考中 · %s", "Thinking · %s")
    val aiEduStatusDone = L("已完成 · %s · %d 步", "Done · %s · %d steps")
    /** 模型自己说完了、但没拿到课程（例如在等用户补充）：不是「停止」 */
    val aiEduStatusIdle = L("已结束 · %s", "Finished · %s")

    /** 思考块的标题：括号里是这一轮的耗时 */
    val aiEduThinkingBlock = L("思考 %s", "Thought for %s")
    /** 流式思考中（正文还在增长） */
    val aiEduThinkingStreaming = L("思考中…", "Thinking…")
    val aiEduExpand = L("展开", "Expand")
    val aiEduCollapse = L("收起", "Collapse")

    val aiEduQueuedNote = L("%d 条补充已排队，下一个动作前送达", "%d queued, sent before the next step")
    val aiEduSteer = L("引导", "Steer")
    val aiEduSteerHint = L("补充要求（下一步前送达）", "Add guidance (sent before the next step)")

    val aiEduPreviewActionN = L("查看预览（%d 门课）", "Preview %d courses")
    val aiEduPreviewTitle = L("导入预览", "Import preview")
    val aiEduPreviewEmpty = L("没有可预览的课程", "No courses to preview")
    /** 预览页提示：同课的连续节次与重复记录已在解析期合并 */
    fun aiEduPreviewMergedN(count: Int): String =
        L("已合并 %d 条课程记录（重复或连续节次）", "Merged %d course entries (duplicates or consecutive periods)")(count)
    /** 预览页提示：缺少上课星期/节次/周次的课被跳过（不猜默认值） */
    fun aiEduPreviewSkippedN(count: Int): String = L(
        "已跳过 %d 门课程：缺少上课星期、节次或周次",
        "Skipped %d courses: missing weekday, period or weeks",
    )(count)
    /** 草稿一门都用不了：告诉用户是「信息缺失被跳过」而不是「解析失败」 */
    fun aiEduDraftNothingUsable(count: Int): String = L(
        "有 %d 门课程缺少上课星期、节次或周次，已全部跳过；请让 AI 补齐后再预览",
        "%d courses are missing weekday, period or weeks and were skipped; ask the AI to fill them in",
    )(count)
    /** 追加导入时全部课程都已在课表里 */
    val aiEduAppendNothingNew = L("没有新增课程：这些课都已在课表里", "Nothing new: these courses already exist")
    val aiEduImportDialogTitle = L("导入到选定的课表？", "Import into the selected schedule?")
    val aiEduTargetSchedule = L("导入到课表", "Import into")
    /** 确认弹窗摘要：目标课表原有门数 → 导入门数 */
    fun aiEduImportTargetSummary(name: String, existing: Int, incoming: Int): String =
        L("「%s」现有 %d 门 → 导入 %d 门", "\"%s\" has %d courses → import %d")(name, existing, incoming)
    val aiEduSchoolLabel = L("学校名称", "School name")
    val aiEduSchoolHint = L(
        "导入后会把这次生成的脚本上传到服务器，同类教务系统的同学可以直接复用",
        "The script will be uploaded so others on the same system can reuse it",
    )
    val aiEduCollapseSheet = L("收起", "Collapse")
    val aiEduBackToChat = L("返回对话", "Back to chat")

    // ── AI 教务导入：脚本历史 ──
    val aiEduHistory = L("历史", "History")
    val aiEduHistoryTitle = L("AI 教务导入历史", "AI Import History")
    val aiEduHistoryEmpty = L("还没有 AI 生成过脚本", "No AI-generated scripts yet")
    val aiEduHistoryEmptyHint = L(
        "完成一次 AI 教务导入后，每个脚本都会留在这里",
        "Every script from an AI import will be kept here",
    )
    val aiEduHistoryViewScript = L("查看脚本", "View script")
    val aiEduHistoryHideScript = L("收起脚本", "Hide script")
    val aiEduHistorySuccess = L("成功 · %d 门课", "OK · %d courses")
    val aiEduHistoryFailed = L("未跑通", "Failed")
    val aiEduHistoryNoNote = L("没有说明", "No note")
}
