package com.haooz.chedule.i18n

/** 课程时间设置页（节次骨架与作息方案）文案 */
object TimeCfgStr {

    // ---- 时段 ----
    val morning = L("上午", "Morning")
    val afternoon = L("下午", "Afternoon")
    val evening = L("晚上", "Evening")

    // ---- 节次骨架 ----
    val sectionCountTitle = L("课表节数", "Schedule Sections")
    val sectionCountDialogTitle = L("课表节数设置", "Set Schedule Sections")
    val sectionsSummaryN = L(
        "上午 %d 节 · 下午 %d 节 · 晚上 %d 节",
        "Morning: %d · Afternoon: %d · Evening: %d",
    )
    val sectionsBriefN = L(
        "上午%d·下午%d·晚上%d · %d 套作息",
        "Morning %d · Afternoon %d · Evening %d · %d routines",
    )
    val sectionsCompactN = L("上%d·下%d·晚%d", "M%d·A%d·E%d")
    val sectionsNote = L(
        "节次决定课表的行数，所有作息方案共用",
        "Sections determine how many rows the schedule has and are shared by all routines",
    )
    val monthN = L("%d月", "%d")

    // ---- 作息方案 ----
    val routinesTitle = L("作息方案", "Routines")
    val effectiveFrom = L("%s生效", "Effective: %s")
    val alwaysActive = L("始终生效", "Always active")
    val activeNow = L("生效中", "Active")
    val addRoutine = L("添加作息", "Add Routine")
    val addRoutineSummary = L("同一套节次，换一套时间", "Same sections, different times")
    val routineNameLabel = L("作息名称", "Routine Name")
    val defaultRoutineName = L("作息", "Routine")
    // ⚠ 中文侧必须与 core TimeConfig.DEFAULT_ROUTINE_NAME 逐字一致(旧数据迁移/兜底配置播种名)
    val seededRoutineName = L("默认作息", "Default routine")
    val effectiveFromN = L("即「%d月%d日」起使用这套时间", "These times take effect from %d/%d")
    val routineDateTaken = L("该日期已有作息，请换一个生效日期", "A routine already exists on this date. Choose another date")

    // ---- 从其他课表复制 ----
    val copyFromSchedule = L("从其他课表复制", "Copy from Another Schedule")
    val copyFromScheduleSummary = L(
        "完整复制该课表的节次与全部作息",
        "Copy all sections and routines from that schedule",
    )
    val noScheduleToCopy = L("没有其他课表可复制", "No other schedule to copy from")
    val selectSchedule = L("选择课表", "Select Schedule")
    val confirmCopy = L("确定复制", "Copy")
    val copiedTimeConfig = L("已复制「%s」的时间配置", "Time settings copied from “%s”")
}
