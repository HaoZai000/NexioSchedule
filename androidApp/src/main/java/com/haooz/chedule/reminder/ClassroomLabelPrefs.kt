package com.haooz.chedule.reminder

import com.haooz.chedule.data.model.ClassroomLabel
import com.haooz.chedule.data.model.ClassroomLabelRule
import com.haooz.chedule.data.storage.KeyValueStore

/**
 * 「通知精简」的地点 / 课程名精简规则读取口。规则在 :core 的 [ClassroomLabel]，这里只提供 store。
 */
internal fun KeyValueStore.classroomLabelRule(): ClassroomLabelRule =
    ClassroomLabel.read(this)

/** 通知里用的地点文案（缩略态与展开态共用）。规则关闭时与课表原文逐字相同。 */
internal fun KeyValueStore.shortClassroom(classroom: String): String =
    ClassroomLabel.apply(classroom, classroomLabelRule())

/** 缩略态用的课程名文案。与地点共用同一套规则与对照表。 */
internal fun KeyValueStore.shortCourse(course: String): String =
    ClassroomLabel.applyCourse(course, classroomLabelRule())
