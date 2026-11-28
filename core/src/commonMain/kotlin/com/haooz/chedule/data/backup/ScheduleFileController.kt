package com.haooz.chedule.data.backup

import com.haooz.chedule.data.platform.showToast

/** 在用户选定的位置读写课表文件；应用备份目录由 [BackupFileController] 管理。 */
interface ScheduleFileController {

    /**
     * 拉起系统文件选择器选一个文本文件，读出全部内容后回调。
     *
     * @param mimeTypes 期望的 mime 列表。各厂商文件管理器上报的 mime 不一致，
     *   限定具体类型会让用户选不到文件，所以实际都是全匹配，合法性由下游把关。
     * @param onResult 读到内容回调文本；用户取消或读取失败回调 `null`
     */
    fun openTextFile(mimeTypes: List<String>, onResult: (String?) -> Unit)

    /**
     * 拉起系统「另存为」（CreateDocument）选择器，把 [content] 写到用户选定的位置。
     *
     * @param fileName 建议文件名（如 `我的课表.json`）
     * @param mimeType `application/json` / `text/calendar`
     * @param content UTF-8 文本
     */
    fun saveTextFile(fileName: String, mimeType: String, content: String)
}

/** 未提供文件选择器时，读取回调 null，保存则提示不支持。 */
object NoopScheduleFileController : ScheduleFileController {
    override fun openTextFile(mimeTypes: List<String>, onResult: (String?) -> Unit) = onResult(null)
    override fun saveTextFile(fileName: String, mimeType: String, content: String) =
        showToast("当前平台不支持导出文件")
}
