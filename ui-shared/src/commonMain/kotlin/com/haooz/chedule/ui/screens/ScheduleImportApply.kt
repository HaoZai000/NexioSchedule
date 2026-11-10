package com.haooz.chedule.ui.screens

import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TeachingWeekReorganization
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.model.TimeRoutine
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.formatSlashDate
import com.haooz.chedule.data.util.minusDays
import com.haooz.chedule.data.util.minusWeeks
import com.haooz.chedule.data.util.todayLocalDate
import com.haooz.chedule.data.util.weeksBetween
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.utils.ThirdPartySharePayload
import com.haooz.chedule.ui.utils.parseShareCourseNote
import com.haooz.chedule.ui.utils.parseShareCourseWeekModel
import com.haooz.chedule.ui.utils.parseShareCourseWeekNotes
import com.haooz.chedule.ui.utils.parseShareCourses
import com.haooz.chedule.ui.utils.parseShareSelectedWeeks
import com.haooz.chedule.ui.utils.parseShareSettings
import com.haooz.chedule.ui.utils.parseShareTotalWeeks
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

// ════════════════════════════════════════════════════════════════════════
//  导入结果**落库**：把解析好的课表 Map / 第三方 payload 写进新建课表
// ════════════════════════════════════════════════════════════════════════

fun applyScheduleData(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    scheduleName: String,
    data: Map<String, Any>,
    /**
     * 撞名时是否允许覆盖。false（默认）保留了「只新建、绝不改旧表」的老语义；
     * 用户在确认弹窗里明确点了「覆盖导入」才传 true —— 口令同步同一张表必然会撞名，
     * 以前这里直接失败，课程一条都进不来。
     */
    overwriteExisting: Boolean = false,
): Pair<Boolean, String> {
    try {
        val nameTaken = scheduleName in scheduleViewModel.scheduleNames.value
        if (nameTaken && !overwriteExisting) {
            return Pair(false, ImportStr.scheduleExists(scheduleName))
        }

        val settings = parseShareSettings(data)
        val reorganizationFieldPresent = settings?.containsKey("teaching_week_reorganizations") == true
        if (reorganizationFieldPresent) {
            val importedStartDate = settings["class_start_time"] as? String
            require(CourseRepository.normalizeClassStartDate(importedStartDate) != null) {
                ImportStr.reorgNeedsStartDate()
            }
        }
        val importTotalWeeks = parseShareTotalWeeks(settings)
        val importedReorganizations = TeachingWeekReorganization.fromBackupValue(
            value = settings?.get("teaching_week_reorganizations"),
            present = reorganizationFieldPresent,
            totalWeeks = importTotalWeeks,
            preserveOutOfRangeRules = true,
        )
        val coursesData = parseShareCourses(data)
        // Keep stored course selections intact when the source semester was shortened later.
        val courseWeekHorizon = CourseRepository.MAX_TOTAL_WEEKS
        val shareCourseWeekModels = coursesData.map {
            parseShareCourseWeekModel(it, maxWeeks = courseWeekHorizon)
        }
        val selectedWeeksByCourse = coursesData.map {
            parseShareSelectedWeeks(it, maxWeeks = courseWeekHorizon)
        }
        val courses = mutableListOf<Course>()
        val courseNameColorMap = mutableMapOf<String, Long>()
        var colorIndex = 0

        val classStartTime = viewModel.classStartTime.value
        val defaultClassStartDate = try {
            LocalDate.parse(classStartTime.replace("/", "-"))
        } catch (_: Exception) {
            val today = todayLocalDate()
            today.minusDays((today.dayOfWeek.isoDayNumber - 1).toLong()).minusWeeks(16)
        }

        // ICS 无开学日设置时，用最早课程所在周的周一反推
        var icsClassStartDate: LocalDate? = null
        coursesData.forEach { courseMap ->
            val startDateStr = courseMap["startDate"] as? String
            if (startDateStr != null && startDateStr.length == 8) {
                val date = try {
                    LocalDate(
                        startDateStr.substring(0, 4).toInt(),
                        startDateStr.substring(4, 6).toInt(),
                        startDateStr.substring(6, 8).toInt()
                    )
                } catch (_: Exception) { null }
                if (date != null) {
                    val monday = date.minusDays((date.dayOfWeek.isoDayNumber - 1).toLong())
                    if (icsClassStartDate == null || monday < icsClassStartDate) {
                        icsClassStartDate = monday
                    }
                }
            }
        }
        val classStartDate = icsClassStartDate ?: defaultClassStartDate

        val classStartMonday = classStartDate.minusDays((classStartDate.dayOfWeek.isoDayNumber - 1).toLong())

        val userSectionTimes = settingsViewModel.sectionTimes.value
        fun findSectionByTime(startMinutes: Int, endMinutes: Int): Pair<Int, Int>? {
            var foundStart: Int? = null
            var foundEnd: Int? = null
            for ((section, timeRange) in userSectionTimes) {
                val parts = timeRange.split("-")
                if (parts.size != 2) continue
                val rangeStartParts = parts[0].split(":")
                val rangeEndParts = parts[1].split(":")
                if (rangeStartParts.size != 2 || rangeEndParts.size != 2) continue
                val rangeStart = (rangeStartParts[0].toIntOrNull() ?: continue) * 60 + (rangeStartParts[1].toIntOrNull() ?: continue)
                val rangeEnd = (rangeEndParts[0].toIntOrNull() ?: continue) * 60 + (rangeEndParts[1].toIntOrNull() ?: continue)
                if (startMinutes in rangeStart until rangeEnd) {
                    foundStart = section
                }
                // 结束时间允许落在节次末尾
                if (endMinutes in (rangeStart + 1)..rangeEnd) {
                    foundEnd = section
                }
            }
            if (foundStart != null && foundEnd != null) {
                return Pair(foundStart, foundEnd)
            }
            // 无精确匹配时退回起始节次
            if (foundStart != null) {
                return Pair(foundStart, foundStart)
            }
            return null
        }

        coursesData.forEachIndexed { courseIndex, courseMap ->
            val name = courseMap["name"] as? String ?: return@forEachIndexed
            val classroom = courseMap["classroom"] as? String ?: ""
            val teacher = courseMap["teacher"] as? String ?: ""
            val dayOfWeek = (courseMap["dayOfWeek"] as? Number)?.toInt() ?: return@forEachIndexed
            var selectedWeeks = selectedWeeksByCourse[courseIndex]
            val explicitWeekModel = shareCourseWeekModels[courseIndex]
            val hasExplicitWeekModel = explicitWeekModel != null

            // JSON 导出有 startSection；ICS 需按时间映射节次
            val directStartSection = (courseMap["startSection"] as? Number)?.toInt()
            val directEndSection = (courseMap["endSection"] as? Number)?.toInt()
            // 自定义时间的课（ICS 里靠 X-NEXIO-CUSTOM-* 带回来）：节次只是占位，
            // 真正的时间来自 customStartTime/customEndTime。跨午夜课如果硬走钟点映射，
            // findSectionByTime 必然失败、整门课被丢掉，所以这里给个兜底节次。
            val isCustomTimeCourse = (courseMap["isCustomTime"] as? Boolean) == true &&
                !(courseMap["customStartTime"] as? String).isNullOrBlank() &&
                !(courseMap["customEndTime"] as? String).isNullOrBlank()
            val sectionPair = if (directStartSection != null && directEndSection != null) {
                Pair(directStartSection, directEndSection)
            } else {
                val startTotalMinutes = (courseMap["startTotalMinutes"] as? Number)?.toInt()
                val endTotalMinutes = (courseMap["endTotalMinutes"] as? Number)?.toInt()
                if (startTotalMinutes != null && endTotalMinutes != null) {
                    findSectionByTime(startTotalMinutes, endTotalMinutes)
                        ?: if (isCustomTimeCourse) Pair(1, 1) else null
                } else null
            }

            if (sectionPair == null) return@forEachIndexed
            val (startSection, endSection) = sectionPair

            if (selectedWeeks.isEmpty() && !hasExplicitWeekModel) {
                @Suppress("UNCHECKED_CAST")
                val datePairs = courseMap["datePairs"] as? List<List<String>>
                if (!datePairs.isNullOrEmpty()) {
                    val allWeeks = mutableSetOf<Int>()
                    for (pair in datePairs) {
                        if (pair.isEmpty()) continue
                        val sd = pair[0]
                        val ud = if (pair.size > 1) pair[1] else ""
                        if (sd.length == 8) {
                            val courseStartDate = try {
                                LocalDate(
                                    sd.substring(0, 4).toInt(),
                                    sd.substring(4, 6).toInt(),
                                    sd.substring(6, 8).toInt()
                                )
                            } catch (_: Exception) { null }

                            if (courseStartDate != null) {
                                val courseMonday = courseStartDate.minusDays((courseStartDate.dayOfWeek.isoDayNumber - 1).toLong())
                                val startWeek = weeksBetween(classStartMonday, courseMonday) + 1

                                val endWeek = if (ud.length == 8) {
                                    val untilDate = try {
                                        LocalDate(
                                            ud.substring(0, 4).toInt(),
                                            ud.substring(4, 6).toInt(),
                                            ud.substring(6, 8).toInt()
                                        )
                                    } catch (_: Exception) { null }
                                    if (untilDate != null) {
                                        val daysDiff = (untilDate.dayOfWeek.isoDayNumber - dayOfWeek + 7) % 7
                                        val lastCourseDate = untilDate.minusDays(daysDiff.toLong())
                                        val lastCourseMonday = lastCourseDate.minusDays((lastCourseDate.dayOfWeek.isoDayNumber - 1).toLong())
                                        weeksBetween(classStartMonday, lastCourseMonday) + 1
                                    } else {
                                        startWeek
                                    }
                                } else {
                                    startWeek
                                }

                                allWeeks.addAll(startWeek..endWeek)
                            }
                        }
                    }
                    selectedWeeks = allWeeks.sorted()
                }
            }

            if (selectedWeeks.isEmpty() && !hasExplicitWeekModel) {
                val countStr = courseMap["count"] as? String
                val startDateStr = courseMap["startDate"] as? String
                if (!countStr.isNullOrEmpty() && !startDateStr.isNullOrEmpty() && startDateStr.length == 8) {
                    val count = countStr.toIntOrNull()
                    if (count != null && count > 0) {
                        val courseStartDate = try {
                            LocalDate(
                                startDateStr.substring(0, 4).toInt(),
                                startDateStr.substring(4, 6).toInt(),
                                startDateStr.substring(6, 8).toInt()
                            )
                        } catch (_: Exception) { null }
                        if (courseStartDate != null) {
                            val courseMonday = courseStartDate.minusDays((courseStartDate.dayOfWeek.isoDayNumber - 1).toLong())
                            val startWeek = weeksBetween(classStartMonday, courseMonday) + 1
                            selectedWeeks = (startWeek until startWeek + count).toList()
                        }
                    }
                }
            }

            if (selectedWeeks.isNotEmpty() || hasExplicitWeekModel) {
                // 导出带 colorRes 时原样还原；旧分享包没有则按课名轮询调色板
                val exportedColor = (courseMap["colorRes"] as? Number)?.toLong()
                val colorRes = exportedColor ?: courseNameColorMap.getOrPut(name) {
                    val color = Course.courseColors[colorIndex % Course.courseColors.size]
                    colorIndex++
                    color
                }
                courses.add(
                    Course(
                        id = randomUuidV4(),
                        name = name,
                        classroom = classroom,
                        teacher = teacher,
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        endSection = endSection,
                        isCustomTime = (courseMap["isCustomTime"] as? Boolean) ?: false,
                        customStartTime = courseMap["customStartTime"] as? String,
                        customEndTime = courseMap["customEndTime"] as? String,
                        startWeek = explicitWeekModel?.startWeek ?: selectedWeeks.min(),
                        endWeek = explicitWeekModel?.endWeek ?: selectedWeeks.max(),
                        weekType = explicitWeekModel?.weekType ?: Course.WEEK_TYPE_ALL,
                        selectedWeeks = selectedWeeks,
                        colorRes = colorRes,
                        // 备注是后加字段：旧分享包没有这两个键，解析成「无备注」
                        note = parseShareCourseNote(courseMap),
                        weekNotes = parseShareCourseWeekNotes(courseMap),
                    )
                )
            }
        }

        require(courses.size == coursesData.size) {
            ImportStr.coursesIncomplete()
        }
        // addSchedule 撞名是 no-op（不会新建），覆盖时直接整表替换课程即可；
        // saveCourses 是「写完整个课表的课程列表」，不会把旧课程留下来
        if (!nameTaken) scheduleViewModel.addSchedule(scheduleName)
        scheduleViewModel.saveCoursesToSchedule(scheduleName, courses)

        // 先刷摘要再继续，否则切换页课程数会短暂错
        scheduleViewModel.refreshScheduleList()

        @Suppress("UNCHECKED_CAST")
        val times = data["times"] as? Map<String, Any>
        // 全套作息（夏令时/冬令时）。旧版本分享的没有这个字段 —— 留空，
        // 由 addTimeConfig 播种单作息，并走下面那段「按 times 写时间」的兼容路径
        val importedRoutines = (data["routines"] as? List<*>)
            ?.mapIndexedNotNull { index, raw ->
                TimeRoutine.fromRaw(raw, fallbackId = index + 1L)
            }
            // 一律按顺序重排 id：payload 自带的 id 可能和 fallback 撞上（A 的 id=2、B 缺 id 落在
            // index=1 就都是 2），重复 id 会让按 id 查找/删除的逻辑行为不确定。这里是全新配置、
            // 没有任何外部引用指向这些 id，重排是安全的。
            ?.mapIndexed { index, routine -> routine.copy(id = (index + 1).toLong()) }
            ?: emptyList()
        val importedMorningSections = (settings?.get("morning_sections") as? Number)?.toInt()
        val importedAfternoonSections = (settings?.get("afternoon_sections") as? Number)?.toInt()
        val importedEveningSections = (settings?.get("evening_sections") as? Number)?.toInt()
        val importedClassDuration = (settings?.get("class_duration") as? Number)?.toInt()?.takeIf { it > 0 }
        val importedBreakDuration = (settings?.get("break_duration") as? Number)?.toInt()?.takeIf { it > 0 }

        if (importedMorningSections != null || importedAfternoonSections != null || importedEveningSections != null || times != null) {
            val sectionTimesMap = mutableMapOf<String, String>()
            if (times != null) {
                @Suppress("UNCHECKED_CAST")
                (times["morning"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["morning_$k"] = v }
                @Suppress("UNCHECKED_CAST")
                (times["afternoon"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["afternoon_$k"] = v }
                @Suppress("UNCHECKED_CAST")
                (times["evening"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["evening_$k"] = v }
            }
            @Suppress("UNCHECKED_CAST")
            val importedSectionNames = (times?.get("section_names") as? Map<String, String>) ?: emptyMap()

            val newConfig = TimeConfig(
                name = scheduleName,
                morningSections = importedMorningSections ?: 4,
                afternoonSections = importedAfternoonSections ?: 4,
                eveningSections = importedEveningSections ?: 4,
                classDuration = importedClassDuration ?: 45,
                shortBreak = importedBreakDuration ?: 10,
                sectionTimes = sectionTimesMap,
                sectionNames = importedSectionNames,
                routines = importedRoutines
            )
            val newConfigId = scheduleViewModel.addTimeConfig(newConfig)
            scheduleViewModel.setScheduleTimeConfigId(scheduleName, newConfigId)
        } else if (!nameTaken) {
            // 分享包里没有作息数据：照抄当前课表那套，但**必须复制成独立配置**。
            // 直接 setScheduleTimeConfigId 绑同一个 id = 两张课表共用一个 TimeConfig，
            // 之后在任一张表里改作息，另一张跟着变（「只同步了作息」的投诉就是这么来的）。
            // 覆盖场景（nameTaken）保留目标课表原有作息，不拿「当前课表那套」换掉它。
            val currentScheduleTimeConfigId = scheduleViewModel.getCurrentScheduleTimeConfigId()
            if (currentScheduleTimeConfigId != 0L) {
                val sourceConfig = scheduleViewModel.getTimeConfig(currentScheduleTimeConfigId)
                val copiedConfigId = scheduleViewModel.addTimeConfig(
                    sourceConfig.copy(id = 0L, name = scheduleName)
                )
                scheduleViewModel.setScheduleTimeConfigId(scheduleName, copiedConfigId)
            }
        }

        if (settings != null) {
            scheduleViewModel.switchToSchedule(scheduleName)
            (data["holiday"] as? Map<*, *>)?.let { raw ->
                runCatching {
                    HolidayManager.decodeBackupData(
                        raw.entries.associate { (k, v) -> k.toString() to v }
                    )
                }.getOrNull()?.let { HolidayManager.restoreBackupDataFor(scheduleName, it) }
            }

            // ⚠ 顺序与条件都是有意的：setCurrentWeek 在「周数与当前不同」时会重算并写回
            // 开学日（那是给「手动选周」设计的 rebase）。导入时两者几乎必然不等 —— 源设备
            // 导出时的周次 ≠ 接收方按今天算出的周次 —— 所以它会把刚写进去的开学日改掉，
            // 表现为整张课表日期整体偏移。current_week 本来就能由日期推出，故只在备份
            // 没有带开学日时才拿它兜底。
            val importedClassStartTime = settings["class_start_time"] as? String
            // ICS 没有开学日字段（IcsCodec 给的 settings 是空 map），上面反推出的周一原本
            // 只用于算周次。不落库的话新表会落到「今天」，第 N 周的课全挤到今天附近 ——
            // 周次编号对、绝对日期全错。这里补写一次（repo 侧对非法日期会直接忽略）。
            val icsStart = icsClassStartDate
            if (importedClassStartTime != null) {
                viewModel.setClassStartTime(importedClassStartTime)
            } else if (icsStart != null && !nameTaken) {
                // 只在新建课表时写：覆盖导入到已有课表时，那张表的开学日是用户自己设的，
                // 不该被 ICS 反推值改掉。
                CourseRepository.getInstance()
                    .setClassStartTime(scheduleName, icsStart.formatSlashDate())
            } else {
                (settings["current_week"] as? Number)?.toInt()?.let { viewModel.setCurrentWeek(it) }
            }
            (settings["total_weeks"] as? Number)?.toInt()?.let { viewModel.setTotalWeeks(it) }
            if (reorganizationFieldPresent && !CourseRepository.getInstance()
                    .setTeachingWeekReorganizations(
                        rules = importedReorganizations,
                        scheduleId = scheduleName,
                        preserveOutOfRangeRules = true,
                    )
            ) {
                throw IllegalArgumentException(ImportStr.reorgWeeksConflict())
            }
            (settings["smart_weekend"] as? Boolean)?.let {
                settingsViewModel.setSmartWeekend(it)
            }
            @Suppress("UNCHECKED_CAST")
            (settings["show_weekend_days"] as? List<Number>)?.let {
                if (it.isNotEmpty()) settingsViewModel.setSmartWeekend(true)
            }
            (settings["show_non_current_week"] as? Boolean)?.let {
                settingsViewModel.setShowNonCurrentWeek(it)
            }
            (settings["morning_sections"] as? Number)?.toInt()?.let { settingsViewModel.setMorningSections(it) }
            (settings["afternoon_sections"] as? Number)?.toInt()?.let { settingsViewModel.setAfternoonSections(it) }
            (settings["evening_sections"] as? Number)?.toInt()?.let { settingsViewModel.setEveningSections(it) }

            // 官方文档 config：firstDayOfWeek（1=周一…7=周日，默认 1）。App 表格固定周一起始，
            // 学校以其它日为周起始时课程 day 相对该周起始，换算回绝对星期防落列错位（与教务导入一致）。
            (settings["first_day_of_week"] as? Number)?.toInt()?.takeIf { it in 2..7 }?.let { firstDay ->
                val repository = CourseRepository.getInstance()
                val currentCourses = repository.getAllCourses()
                if (currentCourses.isNotEmpty()) {
                    val remapped = currentCourses.map { course ->
                        val day = course.dayOfWeek
                        if (day in 1..7) {
                            val absolute = (day - 1 + (firstDay - 1)) % 7 + 1
                            if (absolute != day) course.copy(dayOfWeek = absolute) else course
                        } else {
                            course
                        }
                    }
                    if (remapped.indices.any { remapped[it].dayOfWeek != currentCourses[it].dayOfWeek }) {
                        viewModel.replaceCourses(remapped)
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            val times = data["times"] as? Map<String, Any>
            // 带了全套作息时不再按 times 回写：times 是「分享方导出当天生效那套」，
            // 接收方此刻生效的可能是另一套（跨季节导入），回写会把那套覆盖掉
            if (times != null && importedRoutines.isEmpty()) {
                val morningTimes = mutableMapOf<Int, String>()
                val afternoonTimes = mutableMapOf<Int, String>()
                val eveningTimes = mutableMapOf<Int, String>()

                @Suppress("UNCHECKED_CAST")
                (times["morning"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { morningTimes[it] = v }
                }
                @Suppress("UNCHECKED_CAST")
                (times["afternoon"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { afternoonTimes[it] = v }
                }
                @Suppress("UNCHECKED_CAST")
                (times["evening"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { eveningTimes[it] = v }
                }

                if (morningTimes.isNotEmpty()) settingsViewModel.saveMorningTimes(morningTimes)
                if (afternoonTimes.isNotEmpty()) settingsViewModel.saveAfternoonTimes(afternoonTimes)
                if (eveningTimes.isNotEmpty()) settingsViewModel.saveEveningTimes(eveningTimes)
            }
        }

        viewModel.reloadCourses()
        settingsViewModel.refreshSettings()
        scheduleViewModel.refreshScheduleList()

        return Pair(
            true,
            if (nameTaken) {
                ImportStr.importOverwrittenCompact(scheduleName, courses.size)
            } else {
                ImportStr.importSuccessCompact(scheduleName, courses.size)
            },
        )
    } catch (e: Exception) {
        return Pair(false, ImportStr.importFailedReason(e.message))
    }
}

/** 将 WakeUp / 星链解析结果写入课表（课程 + 可选时间段/学期配置）；撞名语义同 [applyScheduleData] */
fun applyThirdPartySharePayload(
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    scheduleName: String,
    payload: ThirdPartySharePayload,
    /** 撞名时是否允许覆盖（用户在确认弹窗点了「覆盖导入」）；默认仍是「只新建」 */
    overwriteExisting: Boolean = false,
): Pair<Boolean, String> {
    return try {
        val nameTaken = scheduleName in scheduleViewModel.scheduleNames.value
        if (nameTaken && !overwriteExisting) {
            return false to ImportStr.scheduleExists(scheduleName)
        }

        val colorMap = mutableMapOf<String, Long>()
        var colorIndex = 0
        val courses = payload.courses.map { item ->
            val color = colorMap.getOrPut(item.name) {
                Course.courseColors[colorIndex % Course.courseColors.size].also { colorIndex++ }
            }
            item.toCourse(scheduleName, color)
        }
        // 先判空再建表：原来先 addSchedule 再判空，解析不到课程时会留下一张空课表
        if (courses.isEmpty()) {
            return false to ImportStr.noCoursesParsed()
        }
        if (!nameTaken) scheduleViewModel.addSchedule(scheduleName)
        scheduleViewModel.saveCoursesToSchedule(scheduleName, courses)

        if (payload.timeSlots.isNotEmpty()) {
            val repository = CourseRepository()
            val maxSlot = payload.timeSlots.maxOf { it.number }
            val morningSections = settingsViewModel.morningSections.value.coerceAtLeast(1)
            val afternoonSections = settingsViewModel.afternoonSections.value.coerceAtLeast(1)
            var eveningSections = settingsViewModel.eveningSections.value.coerceAtLeast(1)
            val capacity = morningSections + afternoonSections + eveningSections
            if (maxSlot > capacity) {
                eveningSections = (maxSlot - morningSections - afternoonSections).coerceAtLeast(1)
            }
            val morningTimes = mutableMapOf<Int, String>()
            val afternoonTimes = mutableMapOf<Int, String>()
            val eveningTimes = mutableMapOf<Int, String>()
            for (slot in payload.timeSlots) {
                val timeStr = "${slot.startTime}-${slot.endTime}"
                when {
                    slot.number <= morningSections -> morningTimes[slot.number] = timeStr
                    slot.number <= morningSections + afternoonSections ->
                        afternoonTimes[slot.number - morningSections] = timeStr
                    else -> eveningTimes[slot.number - morningSections - afternoonSections] = timeStr
                }
            }
            repository.applyTimeImportToSchedule(
                scheduleName,
                morningSections,
                afternoonSections,
                eveningSections,
                morningTimes,
                afternoonTimes,
                eveningTimes,
            )
        } else if (!nameTaken) {
            // 无时间段时照抄当前课表的作息，但**复制成独立配置**：直接绑同一个 id 会让
            // 两张课表共用一个 TimeConfig，改一张的作息另一张跟着变。
            // 覆盖场景（nameTaken）反而要保留目标课表原有作息，别拿「当前课表那套」换掉它。
            val currentConfigId = scheduleViewModel.getCurrentScheduleTimeConfigId()
            if (currentConfigId != 0L) {
                val sourceConfig = scheduleViewModel.getTimeConfig(currentConfigId)
                val copiedConfigId = scheduleViewModel.addTimeConfig(
                    sourceConfig.copy(id = 0L, name = scheduleName)
                )
                scheduleViewModel.setScheduleTimeConfigId(scheduleName, copiedConfigId)
            }
        }

        scheduleViewModel.switchToSchedule(scheduleName)
        payload.semesterStartDate?.takeIf { it.isNotBlank() }?.let {
            courseViewModel.setClassStartTime(it)
        }
        payload.semesterTotalWeeks?.takeIf { it > 0 }?.let {
            courseViewModel.setTotalWeeks(it)
        }

        courseViewModel.reloadCourses()
        settingsViewModel.refreshSettings()
        scheduleViewModel.refreshScheduleList()
        true to if (nameTaken) {
            ImportStr.importOverwritten(scheduleName, courses.size)
        } else {
            ImportStr.importSuccess(scheduleName, courses.size)
        }
    } catch (e: Exception) {
        false to ImportStr.importFailedReason(e.message)
    }
}
