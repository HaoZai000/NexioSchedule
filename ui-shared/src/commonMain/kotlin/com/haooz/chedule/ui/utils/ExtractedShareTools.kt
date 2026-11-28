package com.haooz.chedule.ui.utils

/** 课表导入页使用的云端适配器 ID，值与仓库中的 adapterId 匹配。 */
object ExtractedShareTools {
    const val WAKEUP = "WakeUp"
    const val STARLINK = "StarLink"
    val ADAPTER_IDS = setOf(WAKEUP, STARLINK)

    const val GLOBAL_TOOLS_SCHOOL_ID = "GLOBAL_TOOLS"
    const val GLOBAL_TOOLS_RESOURCE = "GLOBAL_TOOLS"
    const val WAKEUP_JS = "wake_up.js"
}
