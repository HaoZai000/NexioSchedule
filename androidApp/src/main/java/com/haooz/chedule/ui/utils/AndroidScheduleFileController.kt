package com.haooz.chedule.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import com.haooz.chedule.data.backup.MAX_BACKUP_PARSE_BYTES
import com.haooz.chedule.data.backup.ScheduleFileController
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.i18n.ImportStr

/** 通过 SAF 打开或另存课表文件。使用固定 key 注册选择器，在后台读文件并回主线程交付结果。 */
class AndroidScheduleFileController(private val context: Context) : ScheduleFileController {

    /** 宿主 Activity（SAF 结果回调依赖它；LocalContext 在正常宿主里就是 MainActivity） */
    private val activity: Activity? by lazy { context.findActivity() }

    /** 主线程 Handler：SAF 读写放后台线程，回调（页面要改 Compose 状态）必须回主线程 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 选文件结果回调槽（发起请求前更新；同 key 重复注册 = 替换回调槽，不冲突） */
    private var openResult: ((String?) -> Unit)? = null

    /** 待写入的内容（CreateDocument 回调里只拿得到 Uri，内容必须先存下来） */
    private var pendingContent: String? = null

    private val openLauncher: ActivityResultLauncher<Array<String>>? by lazy {
        (activity as? ActivityResultRegistryOwner)?.activityResultRegistry?.register(
            KEY_OPEN_TEXT,
            ActivityResultContracts.OpenDocument()
        ) { uri -> dispatchOpenResult(uri) }
    }

    /**
     * 另存为的两个 launcher：**契约的 mime 是构造参数**，一个 launcher 只能绑一种 ——
     * JSON 导出要 `application/json`、ICS 导出要 `text/calendar`，
     * 分别注册才能让文件管理器给出正确的类型/后缀。
     */
    private val jsonSaveLauncher: ActivityResultLauncher<String>? by lazy {
        createSaveLauncher(KEY_CREATE_JSON, "application/json")
    }

    private val icsSaveLauncher: ActivityResultLauncher<String>? by lazy {
        createSaveLauncher(KEY_CREATE_ICS, "text/calendar")
    }

    private fun createSaveLauncher(
        key: String,
        mimeType: String,
    ): ActivityResultLauncher<String>? =
        (activity as? ActivityResultRegistryOwner)?.activityResultRegistry?.register(
            key,
            ActivityResultContracts.CreateDocument(mimeType)
        ) { uri -> dispatchSaveResult(uri) }

    // ————————————————————————— 导入：选文件读文本 —————————————————————————

    override fun openTextFile(mimeTypes: List<String>, onResult: (String?) -> Unit) {
        // 先取 launcher（触发注册：恢复态下的陈旧挂起结果在此吞掉，不串到本次回调），再更新槽
        val launcher = openLauncher
        openResult = onResult
        if (launcher == null) {
            // 无 Activity 宿主：拿不到系统选择器，按「用户取消」回调，页面静默
            onResult(null)
            return
        }
        launcher.launch(mimeTypes.toTypedArray())
    }

    private fun dispatchOpenResult(uri: Uri?) {
        val callback = openResult ?: return
        if (uri == null) {
            // 用户取消选择
            callback(null)
            return
        }
        Thread {
            val readResult = runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException(AndroidMiscStr.cannotReadSelectedFile())
                // 8 MB 上限：与备份通道同一个约束（避免把超大/无关文件整个读进内存）
                readBoundedUtf8(inputStream, MAX_BACKUP_PARSE_BYTES)
            }
            mainHandler.post {
                readResult.fold(
                    onSuccess = { text -> callback(text) },
                    onFailure = { e ->
                        showToast(ImportStr.importFailedReason(e.message))
                        callback(null)
                    }
                )
            }
        }.start()
    }

    // ————————————————————————— 导出：另存为 —————————————————————————

    override fun saveTextFile(fileName: String, mimeType: String, content: String) {
        val launcher = when {
            mimeType.contains("calendar") -> icsSaveLauncher
            else -> jsonSaveLauncher
        }
        if (launcher == null) {
            showToast(AndroidMiscStr.exportFailedN(AndroidMiscStr.cannotOpenFilePicker()))
            return
        }
        pendingContent = content
        launcher.launch(fileName)
    }

    private fun dispatchSaveResult(uri: Uri?) {
        val content = pendingContent
        pendingContent = null
        if (uri == null || content == null) {
            // 用户取消不提示错误。
            return
        }
        Thread {
            val writeResult = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException(AndroidMiscStr.cannotWriteSelectedLocation())
            }
            mainHandler.post {
                writeResult.fold(
                    onSuccess = { showToast(AndroidMiscStr.exportSuccess()) },
                    onFailure = { e -> showToast(AndroidMiscStr.exportFailedN(e.message)) }
                )
            }
        }.start()
    }

    private companion object {
        const val KEY_OPEN_TEXT = "com.haooz.chedule.schedulefile.open_text"
        const val KEY_CREATE_JSON = "com.haooz.chedule.schedulefile.create_json"
        const val KEY_CREATE_ICS = "com.haooz.chedule.schedulefile.create_ics"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
