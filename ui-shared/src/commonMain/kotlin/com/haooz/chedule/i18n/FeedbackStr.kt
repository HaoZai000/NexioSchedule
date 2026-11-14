package com.haooz.chedule.i18n

import com.haooz.chedule.data.platform.FeedbackKind

/**
 * 应用内反馈与开发进度的全部文案。
 *
 * 状态标签优先用服务端下发的 `status_label`（未知状态也能显示中文），
 * 这里的本地映射只用在两处：`status_label` 缺失时的兜底、以及时间线条目
 * （服务端只给 `status` 原始 key）。
 */
object FeedbackStr {

    // ---- 页面标题 ----

    val title = L("应用内反馈", "In-App Feedback")
    val historyTitle = L("我的反馈", "My Feedback")
    val roadmapTitle = L("开发进度", "Development Progress")

    // ---- 表单 ----

    val typeBug = L("Bug 反馈", "Bug report")
    val typeFeature = L("功能建议", "Feature request")

    val titleLabel = L("标题（可选）", "Title (optional)")

    /**
     * 详情框的占位文案：直接给填写结构，比一句「具体内容」有用得多。
     * 多行原样显示在空输入框里，开始输入即隐藏（不留浮动 label，否则 4 行字会糊在框顶）。
     */
    val contentPlaceholder = L(
        "请详细描述您遇到的问题：\n" +
            "1. 打开了某个页面或功能\n" +
            "2. 执行某个操作遇到了什么问题\n" +
            "3. 其他附加描述",
        "Please describe the issue:\n" +
            "1. Which page or function you opened\n" +
            "2. What you did and what went wrong\n" +
            "3. Anything else worth mentioning",
    )
    val contentCounter = L("%d / %d", "%d / %d")

    val emailLabel = L("邮箱（可选）", "Email (optional)")
    val emailInvalid = L("邮箱格式看起来不对", "That email doesn't look right")

    val submit = L("提交", "Submit")
    val submitting = L("提交中…", "Submitting…")
    val submitTooShort = L("再多写几个字吧（至少 5 个字）", "Please write a bit more (at least 5 characters)")

    val successTitle = L("已收到你的反馈", "Feedback received")
    val successBody = L("会尽快查看。单号 %s", "We'll take a look soon. ID %s")
    val successHint = L("处理进展会同步到开发进度页", "Progress shows up on the roadmap page")
    val writeAnother = L("再写一条", "Write another")
    val viewMine = L("查看我的反馈", "My feedback")
    val viewRoadmap = L("查看开发进度", "Development progress")

    // ---- 删除 ----

    val delete = L("删除", "Delete")
    val deleteTitle = L("删除这条反馈？", "Delete this feedback?")
    val deleteBody = L("删除后不可恢复，开发进度里的这条也会一起移除。", "This cannot be undone and the roadmap entry will be removed too.")
    val deleteFailed = L("删除失败", "Delete failed")

    // ---- 我的反馈 ----

    val historyEmpty = L("还没有提交过反馈", "No feedback yet")
    val historyEmptyBody = L("遇到问题或有想法，都可以在这里告诉我们", "Tell us about any issue or idea right here")
    val loading = L("正在加载…", "Loading…")
    val loadFailed = L("加载失败", "Failed to load")
    val retry = L("重试", "Retry")
    val noTitle = L("（无标题）", "(No title)")

    // ---- 开发进度 ----

    val roadmapEmpty = L("暂时还没有公开的进度", "Nothing published yet")
    val roadmapEmptyBody = L("开发中的功能和修复会在这里更新", "Work in progress will show up here")
    val filterAll = L("全部", "All")
    val filterFeature = L("新功能", "New features")
    val filterBug = L("Bug 修复", "Bug fixes")
    val filterEmpty = L("没有符合筛选的条目", "Nothing matches this filter")

    val versionTag = L("版本 %s", "Version %s")
    /** 官方回复（对外说明）的特殊标注 */
    val officialReply = L("官方回复", "Official reply")

    // ---- 进度条节点（5 段，与服务端 9 个状态的压缩关系见 FeedbackStage）----

    val stageReceived = L("已收到", "Received")
    val stagePending = L("待处理", "Pending")
    val stagePlanned = L("已排期", "Planned")
    val stageInProgress = L("开发中", "In progress")
    val stageFixing = L("修复中", "Fixing")
    val stagePendingRelease = L("待发版", "Awaiting release")
    val stageReleased = L("已发布", "Released")
    val stageClosed = L("已关闭", "Closed")
    val stageClosedWithReason = L("已结束 · %s", "Closed · %s")

    // ---- 状态标签 ----

    private val statusPending = L("待处理", "Pending")
    private val statusReceived = L("已收到", "Received")
    private val statusReviewing = L("评估中", "Under review")
    private val statusPlanned = L("已排期", "Planned")
    private val statusInProgress = L("开发中", "In progress")
    private val statusFixing = L("修复中", "Fixing")
    private val statusDone = L("开发完成", "Done")
    private val statusPendingRelease = L("开发完成待发版", "Awaiting release")
    private val statusReleased = L("已发版", "Released")
    private val statusRejected = L("暂不采纳", "Not planned")
    private val statusDuplicate = L("重复提交", "Duplicate")

    /**
     * 状态 → 展示文案。
     *
     * @param fallback 服务端下发的 `status_label`，仅在本地没有该 key 时使用；
     *   两者都没有就原样显示 key（宁可露出英文，也不显示空白）
     */
    fun statusLabel(status: String, kind: FeedbackKind = FeedbackKind.Feature, fallback: String = ""): String =
        when (status) {
            "pending" -> statusPending()
            "received" -> statusReceived()
            "reviewing" -> statusReviewing()
            "planned" -> statusPlanned()
            // 同一状态在 Bug / 新功能上措辞不同：修 Bug 说「修复中」，做功能说「开发中」
            "in_progress" -> if (kind == FeedbackKind.Bug) statusFixing() else statusInProgress()
            "done" -> statusDone()
            "pending_release" -> statusPendingRelease()
            "released" -> statusReleased()
            "rejected" -> statusRejected()
            "duplicate" -> statusDuplicate()
            else -> fallback.ifBlank { status }
        }
}
