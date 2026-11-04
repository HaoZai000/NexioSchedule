package com.haooz.chedule.i18n

/** 切换课表 / 课程管理 / 排班模式相关页面文案 */
object SwitchStr {
    // ⚠ 中文侧必须与 core CourseRepository.DEFAULT_FOLDER_NAME 逐字一致
    val defaultFolderName = L("默认文件夹", "Default folder")

    // —— 切换课表（列表与标题） ——
    val editSchedule = L("编辑课表", "Edit Schedule")
    val allSchedules = L("全部课表", "All Schedules")
    val switchSchedule = L("切换课表", "Switch Schedule")
    val closeEdit = L("关闭编辑", "Done Editing")
    val addSchedule = L("添加课表", "Add Schedule")
    val newSchedule = L("新建课表", "New Schedule")
    val scheduleNameLabel = L("课表名称", "Schedule Name")
    val nameRequired = L("请输入课表名称", "Please enter a schedule name")
    val duplicateSchedule = L("已存在同名课表", "A schedule with this name already exists")
    val currentSchedule = L("当前课表", "Current schedule")

    // —— 文件夹 ——
    val newFolder = L("新建文件夹", "New Folder")
    val folderNameLabel = L("文件夹名称", "Folder Name")
    val renameFolder = L("重命名文件夹", "Rename Folder")
    val duplicateFolder = L("已存在同名文件夹", "A folder with this name already exists")
    val folder = L("文件夹", "Folder")
    val folderEmpty = L("文件夹内暂无课表", "No schedules in this folder")
    val scheduleCountN = L("%d 个课表", "%d schedules")

    // —— 编辑底栏 ——
    val share = L("分享", "Share")
    val move = L("移动", "Move")
    val cannotShareEmpty = L("「%s」课表为空，无法分享", "\"%s\" is empty and cannot be shared")

    // —— 分享确认 ——
    val shareScheduleTitle = L("分享课表", "Share Schedule")
    val shareConfirmSummary =
        L("将课表「%s」上传生成分享口令？\n口令有效期有限，过期后可重新生成", "Upload schedule \"%s\" to generate a share code?\nThe code expires after a while; you can generate a new one anytime")
    val confirmShare = L("确认分享", "Share")

    // —— 删除 / 解散确认 ——
    val deleteSchedulesAndFolders = L("删除课表与文件夹", "Delete Schedules and Folders")
    val disbandFolder = L("解散文件夹", "Disband Folder")
    val deleteSchedule = L("删除课表", "Delete Schedule")
    val deleteMixedConfirm = L(
        "选中的 %d 个课表将被删除；选中的 %d 个文件夹将解散，其中课表移回「全部课表」。",
        "The %d selected schedules will be deleted; the %d selected folders will be disbanded and their schedules moved back to All Schedules."
    )
    val disbandOnlyConfirm = L(
        "选中的文件夹将被解散，其中课表移回「全部课表」，课表不会被删除。",
        "The selected folders will be disbanded and their schedules moved back to All Schedules; the schedules will not be deleted."
    )
    val deleteSchedulesConfirm = L("确定要删除选中的课表吗？", "Delete the selected schedules?")
    val disband = L("解散", "Disband")

    // —— 移动到 ——
    val moveTo = L("移动到", "Move To")
    val moveSelectedCount = L("已选中 %d 个课表", "%d schedules selected")
    val moveToRoot = L("移出文件夹，回到根目录", "Remove from folder and move to root")

    // —— 课程管理 ——
    val courseManage = L("课程管理", "Course Management")
    val noCourses = L("暂无课程", "No courses")
    val noCoursesHint = L("还没有添加任何课程", "No courses added yet")
    val editCourse = L("编辑课程", "Edit Course")
    val newCourse = L("新建课程", "New Course")
    val courseNameLabel = L("课程名称", "Course Name")
    val courseColor = L("课程颜色", "Course Color")
    val customColor = L("自定义颜色", "Custom color")
    val selectColor = L("选择颜色", "Select Color")
    val duplicateCourse = L("已存在同名课程", "A course with this name already exists")
    val deleteCourse = L("删除课程", "Delete Course")
    val deleteCourseRecordsConfirm = L(
        "确定要删除课程「%s」的 %d 条记录吗？\n此操作不可撤销。",
        "Delete course \"%s\" and its %d records?\nThis action cannot be undone."
    )
    val deleteCourseConfirm = L(
        "确定要删除课程「%s」吗？\n此操作不可撤销。",
        "Delete course \"%s\"?\nThis action cannot be undone."
    )
    val sectionCountN = L("%d 节", "%dP")
    val tapToSetTime = L("点击设置时间", "Tap to set time")
    val selectCourseHint = L("请选择左侧课程", "Select a course on the left")

    // —— 排班模式 ——
    val lunchBreak = L("午休", "Lunch break")
    val dinnerBreak = L("晚休", "Dinner break")
    val scheduleDetail = L("课表详情", "Schedule Details")

    // —— 平板学期课表预览 ——
    val semesterPreview = L("学期课表预览", "Semester schedule preview")
    val scheduleEmptyHint = L("这张课表还没有课程", "This schedule has no courses yet")
    val dayHeaderMon = L("一", "M")
    val dayHeaderTue = L("二", "T")
    val dayHeaderWed = L("三", "W")
    val dayHeaderThu = L("四", "T")
    val dayHeaderFri = L("五", "F")
    val dayHeaderSat = L("六", "S")
    val dayHeaderSun = L("日", "S")
}
