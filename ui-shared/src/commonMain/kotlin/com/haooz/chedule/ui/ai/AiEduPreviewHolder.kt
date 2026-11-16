package com.haooz.chedule.ui.ai

import com.haooz.chedule.data.model.Course

/**
 * 对话页 → 独立预览页 的草稿传递（路由只有稳定 id，没有参数通道）。
 *
 * 预览页退出时清空：下次进来不能拿到上一轮的课程和配置。
 */
internal object AiEduPreviewHolder {

    var draft: EduAgentDraft? = null
        private set

    var courses: List<Course> = emptyList()
        private set

    /** 草稿里合并掉的重复课程条数（供预览页提示「已合并 N 条」） */
    var mergedDuplicates: Int = 0
        private set

    /** 因缺上课星期/节次/周次被跳过的课程名（供预览页提示「已跳过」） */
    var skippedNames: List<String> = emptyList()
        private set

    fun set(
        draft: EduAgentDraft,
        courses: List<Course>,
        mergedDuplicates: Int = 0,
        skippedNames: List<String> = emptyList(),
    ) {
        this.draft = draft
        this.courses = courses
        this.mergedDuplicates = mergedDuplicates
        this.skippedNames = skippedNames
    }

    fun clear() {
        draft = null
        courses = emptyList()
        mergedDuplicates = 0
        skippedNames = emptyList()
    }
}
