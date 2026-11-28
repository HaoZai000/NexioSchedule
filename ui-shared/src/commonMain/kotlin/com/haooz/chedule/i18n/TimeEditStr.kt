package com.haooz.chedule.i18n

/** 时间配置编辑页（编辑作息：节次时间、快捷设置、特殊时间块）文案 */
object TimeEditStr {

    // ---- 页面骨架 ----
    val editRoutine = L("编辑作息", "Edit Routine")
    val saveAndClose = L("保存并关闭", "Save & Close")

    // ---- 作息元信息 ----
    val routineNamePlaceholder = L("请输入作息名称", "Enter a routine name")
    val effectiveDate = L("生效日期", "Effective Date")
    val effectiveDateSummaryN = L("每年 %d月%d日起自动切换", "Switches automatically every year starting %d/%d")
    val effectiveDateDialogSummary = L("到这一天自动切换到该作息", "Switches to this routine automatically on this date")
    val deleteRoutine = L("删除作息", "Delete Routine")
    val deleteRoutineConfirm = L(
        "确定要删除作息「%s」吗？\n此操作不可撤销。",
        "Delete routine \"%s\"?\nThis action cannot be undone.",
    )

    // ---- 快捷设置时间 ----
    val quickTimeSettings = L("快捷设置时间", "Quick Time Setup")
    val classDurationTitle = L("每节课时长", "Class Duration")
    val shortBreakTitle = L("课间休息", "Short Break")
    val shortBreakDurationTitle = L("小课间休息时长", "Short Break Duration")
    val longBreakTitle = L("大课间休息", "Long Break")
    val longBreakMorningTitle = L("上午大课间休息", "Morning Long Break")
    val longBreakAfternoonTitle = L("下午大课间休息", "Afternoon Long Break")
    val longBreakEveningTitle = L("晚上大课间休息", "Evening Long Break")
    val longBreakMorningSetting = L("上午大课间设置", "Morning Long Break Settings")
    val longBreakAfternoonSetting = L("下午大课间设置", "Afternoon Long Break Settings")
    val longBreakEveningSetting = L("晚上大课间设置", "Evening Long Break Settings")
    val morningStartTitle = L("上午开始时间", "Morning Start")
    val afternoonStartTitle = L("下午开始时间", "Afternoon Start")
    val eveningStartTitle = L("晚上开始时间", "Evening Start")
    val minutesN = L("%d分钟", "%d min")
    val afterSectionN = L("第%d节后", "After period %d")
    val afterSectionMinutesN = L("第%d节后 %d分钟", "After period %d, %d min")
    val apply = L("应用", "Apply")
    val timeApplied = L("课程时间已应用", "Class times applied")

    // ---- 节次时间 ----
    val sectionN = L("第%d节", "Period %d")
    val sectionNameN = L("第%d节 %s", "Period %d: %s")
    val sectionTimeSettingsN = L("第%d节时间设置", "Set Period %d Time")
    val timeSettings = L("时间设置", "Time Settings")
    val customSectionName = L("自定义节次名称", "Custom period name")
    val morningSectionN = L("上午第%d节", "Morning period %d")
    val afternoonSectionN = L("下午第%d节", "Afternoon period %d")
    val eveningSectionN = L("晚上第%d节", "Evening period %d")
    val overlapPair = L("%s 与 %s", "%s and %s")

    // ---- 特殊时间块 ----
    val specialBlocksTitle = L("特殊课程", "Special Blocks")
    val specialCourse = L("特殊课程", "Special block")
    val noSpecialBlocks = L("暂无特殊课程", "No special blocks yet")
    val noSpecialBlocksSummary = L(
        "点击添加早读、眼保健操等无编号时段",
        "Tap to add unnumbered blocks such as morning reading or eye exercises",
    )
    val addSpecialBlock = L("添加特殊课程", "Add Special Block")
    val editSpecialBlock = L("编辑特殊课程", "Edit Special Block")
    val deleteSpecialBlock = L("删除特殊课程", "Delete Special Block")
    val deleteSpecialBlockConfirm = L(
        "确定要删除这条特殊课程吗？\n此操作不可撤销。",
        "Delete this special block?\nThis action cannot be undone.",
    )
    val weekdaySchedule = L("按星期的安排", "Weekly Arrangement")

    // ---- 时间重叠 ----
    val timeOverlap = L("时间重叠", "Time Overlap")
    val timeOverlapBlocking = L("存在时间重叠，请重新设置", "There is a time overlap. Please adjust the times")
    val timeOverlapWarningTitle = L("时间重叠（可继续保存）", "Time Overlap (Save Anyway)")
    val overlapWarningBody = L(
        "特殊课程会排在该时段之前，确认无误可继续保存",
        "Special blocks will appear before the overlapping periods. Save anyway if that looks right",
    )
    val backToEdit = L("返回修改", "Go Back to Edit")
    val saveAnyway = L("仍然保存", "Save Anyway")
    val everyDay = L("每天", "Every day")
}
