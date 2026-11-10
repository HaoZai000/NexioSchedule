package com.haooz.chedule.ui.utils

/**
 * 各功能统一埋点入口：稳定短名，便于崩溃轨迹对账。真正的轨迹记录由宿主经 [init] 注入，
 * 未注入时静默丢弃（埋点丢失不该反过来搞崩业务）。
 *
 * ⚠ 包名必须保持 `com.haooz.chedule.ui.utils` —— 21 处调用方的 import 依赖它。
 */
object FeatureLog {

    private var traceBackend: ((feature: String, event: String, detail: String) -> Unit)? = null
    private var recordingBackend: ((feature: String, event: String, detail: String) -> Unit)? = null
    private var recordingActive: (() -> Boolean)? = null

    /** 由宿主注入轨迹记录实现。重复调用会覆盖（便于测试）。 */
    fun init(
        trace: (feature: String, event: String, detail: String) -> Unit,
        recording: (feature: String, event: String, detail: String) -> Unit,
        isRecording: () -> Boolean,
    ) {
        traceBackend = trace
        recordingBackend = recording
        recordingActive = isRecording
    }

    fun t(feature: String, event: String, detail: String = "") {
        traceBackend?.invoke(feature, event, detail)
    }

    fun switchSchedule(event: String, detail: String = "") =
        t("切换课表", event, detail)

    fun main(event: String, detail: String = "") =
        t("主页", event, detail)

    fun settings(page: String) =
        t("设置", "open", page)

    fun today(event: String, detail: String = "") =
        t("今日页", event, detail)

    fun schedule(event: String, detail: String = "") =
        t("课程表", event, detail)

    fun course(event: String, detail: String = "") =
        t("课程", event, detail)

    fun appearance(event: String, detail: String = "") =
        t("课表外观", event, detail)

    fun shift(event: String, detail: String = "") =
        t("排班课表", event, detail)

    fun import(event: String, detail: String = "") =
        t("课表导入", event, detail)

    fun backup(event: String, detail: String = "") =
        t("备份迁移", event, detail)

    fun reminder(event: String, detail: String = "") =
        t("课程提醒", event, detail)

    /** 课程提醒页详细流程：仅「开始录制」后写入 */
    fun reminderFlow(event: String, detail: String = "") {
        recordingBackend?.invoke("课程提醒", event, detail)
    }

    /**
     * lambda 版：未录制时 detail 不求值。
     *
     * ⚠ 刻意不用 `inline`：本模块 android 变体的 JVM target 是 21、`:androidApp` 是 17，
     * 跨模块 inline 会直接编译失败（"Cannot inline bytecode built with JVM target 21 …"），
     * 而 KMP 的 android 目标在当前 AGP 下不暴露 `compileOptions`，无法对齐 target。
     * 代价仅是 lambda 对象构造一次，lambda 体仍不执行。
     */
    fun reminderFlow(event: String, detail: () -> String) {
        if (recordingActive?.invoke() != true) return
        recordingBackend?.invoke("课程提醒", event, detail())
    }

    fun holiday(event: String, detail: String = "") =
        t("节假日调休", event, detail)

    fun widget(event: String, detail: String = "") =
        t("桌面小部件", event, detail)

    fun preference(event: String, detail: String = "") =
        t("应用偏好", event, detail)

    fun update(event: String, detail: String = "") =
        t("更新", event, detail)

    fun about(event: String, detail: String = "") =
        t("关于应用", event, detail)

    fun timeConfig(event: String, detail: String = "") =
        t("课表节数时间", event, detail)

    fun webdav(event: String, detail: String = "") =
        t("WebDAV", event, detail)

    fun shareSchedule(event: String, detail: String = "") =
        t("口令分享", event, detail)
}
