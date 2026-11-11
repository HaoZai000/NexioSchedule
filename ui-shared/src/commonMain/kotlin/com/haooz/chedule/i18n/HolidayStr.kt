package com.haooz.chedule.i18n

/** 节假日设置页（节假日 / 调休工作日 / 教学周重组 / 节假日课程排除）文案 */
object HolidayStr {

    // ---- 星期 ----

    /** 星期一～星期日完整称呼（周次选择器、条目摘要）。 */
    val weekdays = listOf(
        L("星期一", "Monday"),
        L("星期二", "Tuesday"),
        L("星期三", "Wednesday"),
        L("星期四", "Thursday"),
        L("星期五", "Friday"),
        L("星期六", "Saturday"),
        L("星期日", "Sunday"),
    )

    /** 星期范围选择器格子里的单字简称。 */
    val weekdayChars = listOf(
        L("一", "M"),
        L("二", "T"),
        L("三", "W"),
        L("四", "T"),
        L("五", "F"),
        L("六", "S"),
        L("日", "S"),
    )

    // ---- 通用 ----
    val monthN = L("%d月", "%d")
    val prevMonth = L("上个月", "Previous month")
    val nextMonth = L("下个月", "Next month")
    val makeupConnector = L("补", "→")
    val pickDateShort = L("选择日期", "Pick date")
    val weekWeekdayN = L("第%d周%s", "Week %d %s")
    val dateOutOfRange = L("日期超出范围", "Date out of range")
    val dateUnavailable = L("日期不可用", "Date unavailable")
    val dateRangeTo = L(" 至 %s", " to %s")
    val yearLabel = L("年份", "Year")
    val nameLabel = L("名称", "Name")
    val adjustedDateLabel = L("调休日期", "Adjusted Workday Date")
    val startLabel = L("开始", "Start")
    val endLabel = L("结束", "End")
    val sectionRangeLabel = L("节次范围", "Section Range")
    val sectionRangeN = L("第%d-%d节", "Periods %d-%d")

    // ---- 页面分段 ----
    val tabAnnual = L("年度安排", "This Year")
    val tabOther = L("其他操作", "More")

    // ---- 节假日 / 调休工作日 ----
    val annualTitle = L("假期与调休", "Holidays & Adjusted Days")
    val annualDesc = L(
        "• 假期日期不会发送课程提醒\n• 调休日按补课安排调度课程",
        "• No course reminders are sent on holiday dates\n• Adjusted workdays follow the configured makeup classes",
    )
    val typeHolidayTag = L("假", "Off")
    val typeWorkswapTag = L("班", "Work")
    val holidayCountN = L("%d 个假期", "%d holidays")
    val workswapCountN = L("%d 个调休日", "%d adjusted workdays")
    val totalDaysN = L("共 %d 天", "%d days")
    val yearMonthN = L("%d年%d月", "%d/%d")
    val holidayDateRangeLabel = L("假期日期", "Holiday Dates")
    val rangePickHint = L("点选开始日和结束日", "Pick a start and end date")
    val emptyAnnualHint = L("本年度还没有假期或调休", "No holidays or adjusted days for this year")
    val emptyAnnualDesc = L(
        "一键导入法定节假日与补班安排",
        "Import statutory holidays and makeup days in one tap, editable afterwards",
    )
    val importForYearN = L("导入 %d 年法定节假日", "Import %d statutory holidays")
    val syncingLabel = L("同步中…", "Syncing…")
    val defaultHolidayName = L("节假日", "Holiday")
    val defaultWorkswapName = L("调休工作日", "Adjusted workday")
    val addHoliday = L("添加节假日", "Add Holiday")
    val addWorkswap = L("添加调休工作日", "Add Adjusted Workday")
    val editHoliday = L("编辑节假日", "Edit Holiday")
    val editWorkswap = L("编辑调休工作日", "Edit Adjusted Workday")
    val followCourseLabel = L("跟随课程", "Course to Follow")
    val followPrefillNote = L(
        "已按假期末尾推算预填，请以学校通知为准，保存后生效",
        "Pre-filled from the end of the holiday. Follow your school's official notice; takes effect after saving",
    )
    val makeupPending = L("待配置补班课程", "Makeup classes pending")
    val classOnN = L("上 %d月%d日 的课", "Class on %d/%d")
    val deleteRecord = L("删除记录", "Delete Record")
    val deleteHolidayConfirm = L(
        "确定要删除这条节假日记录吗？\n此操作不可撤销。",
        "Delete this holiday record?\nThis action cannot be undone.",
    )
    val deleteWorkswapConfirm = L(
        "确定要删除这条调休工作日记录吗？\n此操作不可撤销。",
        "Delete this adjusted workday record?\nThis action cannot be undone.",
    )

    // ---- 校验 / 提示 ----
    val invalidDate = L("日期格式不正确", "Invalid date format")
    val endBeforeStart = L("结束日期不能早于开始日期", "End date cannot be earlier than start date")
    val selectValidFollow = L("请明确选择有效的跟随周次和星期", "Please select a valid follow week and weekday")
    val followWeekNoDate = L("该周次在当前课表下没有对应日期", "This week has no corresponding date in the current schedule")
    val dataReadFailedSave = L("节假日数据无法读取，未覆盖原数据", "Holiday data could not be read; original data was not overwritten")
    val dataReadFailedDelete = L("节假日数据无法读取，未删除原数据", "Holiday data could not be read; original data was not deleted")
    val saveFailedUnchanged = L("保存失败，现有规则未更改", "Save failed; existing rules remain unchanged")
    val deleteFailedUnchanged = L("删除失败，现有规则未更改", "Delete failed; existing rules remain unchanged")
    val noSectionsConfigured = L("当前课表尚未配置节次", "No sections configured in the current schedule")
    val sectionRangeChanged = L("当前课表节次范围已变化，请重新选择", "The section range of the current schedule has changed. Please select again")
    val noWeekToReorganize = L("当前课表没有可用的原始周次可重组", "No original week is available to reorganize in the current schedule")
    val enterNameFirst = L("请先填写名称", "Enter a name first")
    val cannotSave = L("无法保存", "Cannot save")
    val cannotSaveWithError = L("无法保存：%s", "Cannot save: %s")

    // ---- 教学周重组 ----
    val reorgTitle = L("教学周重组", "Teaching Week Reorganization")
    val reorgDesc = L(
        "• 长假可能带来教学周的重组，并引发后续教学周的顺延更改\n• 可在此进行教学周重组，后续教学周将重新分配计算",
        "• Long breaks may reorganize teaching weeks and shift the following ones\n• Reorganize teaching weeks here; subsequent weeks will be recalculated",
    )
    val reorgRuleN = L("重组规则 %d", "Rule %d")
    val originalWeekN = L("原始第%d周", "Original week %d")
    val teachingWeekN = L("教学第%d周", "Teaching week %d")
    val addReorgRule = L("添加教学周重组规则", "Add Reorganization Rule")
    val editReorgRule = L("编辑教学周重组规则", "Edit Reorganization Rule")
    val deleteReorgRule = L("删除教学周重组规则", "Delete Reorganization Rule")
    val deleteReorgConfirm = L(
        "删除后，受影响的后续教学周会重新计算。确定删除这条规则吗？",
        "After deletion, affected subsequent teaching weeks will be recalculated. Delete this rule?",
    )
    val reorgPreview = L("重组预览", "Reorganization Preview")
    val firstHalfWeeksLabel = L("上半段周次", "First Half Weeks")
    val secondHalfWeeksLabel = L("下半段周次", "Second Half Weeks")
    val firstHalfWeekdaysLabel = L("上半段星期", "First Half Weekdays")
    val secondHalfWeekdaysLabel = L("下半段星期", "Second Half Weekdays")
    val continuousRange = L("连续范围", "Continuous range")
    val firstHalfOriginalWeekN = L("上半段 · 原第%d周", "First half · Original week %d")
    val secondHalfOriginalWeekN = L("下半段 · 原第%d周", "Second half · Original week %d")
    val coursesPaused = L("课程暂停", "Classes Paused")
    val pickOriginalWeek = L("选择原始周", "Select Original Week")
    val weeksShiftedN = L("后续顺延 +%d周", "Following weeks +%d")
    val laterUnchanged = L("后续不变", "Following weeks unchanged")
    val mergedInto = L("合并为%s", "Merged into %s")

    // ---- 节假日课程排除 ----
    val exclusionTitle = L("节假日课程排除", "Holiday Course Exclusion")
    val exclusionDesc = L(
        "• 假期前一天或最后一天的几节课不用上课\n" +
            "• 可以将节假日前后的某几节课进行排除\n" +
            "「假期前一天即使是调休上班日也会照常应用该规则」",
        "• Some classes on the day before or the last day of a holiday can be skipped\n" +
            "• Exclude specific sections around holidays\n" +
            "“The rule also applies on the day before a holiday, even if it is an adjusted workday”",
    )
    val beforeExclusionTitle = L("假期前日课程排除", "Exclude Courses on Day Before Holiday")
    val endExclusionTitle = L("假期末天课程排除", "Exclude Courses on Last Day of Holiday")
    val pickSections = L("选择上课节次", "Select Periods")
}
