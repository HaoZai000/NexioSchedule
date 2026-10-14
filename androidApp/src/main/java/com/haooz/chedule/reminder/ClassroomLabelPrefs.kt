package com.haooz.chedule.reminder

import android.content.SharedPreferences
import com.haooz.chedule.data.ClassroomLabel
import com.haooz.chedule.data.ClassroomLabelRule

/**
 * 缩略态（超级岛槽位 / 灵动岛胶囊 / 实况通知右侧 / 息屏标题）地点精简规则的读取口。
 *
 * 规则本身 —— 怎么精简、可用宽度怎么算 —— 在 :core 的 [ClassroomLabel] 里，两端共用。
 * 这里只把 `course_reminder_prefs` 的三个键读出来：发通知的是 Receiver / Service，
 * 直读 SharedPreferences 比走 AppStorage 少一层「Application 是否已 init」的假设。
 */
internal fun SharedPreferences.classroomLabelRule(): ClassroomLabelRule = ClassroomLabelRule(
    mode = getInt(ClassroomLabel.KEY_MODE, ClassroomLabel.MODE_RAW),
    pattern = getString(ClassroomLabel.KEY_PATTERN, "").orEmpty(),
    replacement = getString(ClassroomLabel.KEY_REPLACEMENT, "").orEmpty(),
)

/** 缩略态用的地点文案。规则为「原样显示」时与课表原文逐字相同。 */
internal fun SharedPreferences.shortClassroom(classroom: String): String =
    ClassroomLabel.apply(classroom, classroomLabelRule())
