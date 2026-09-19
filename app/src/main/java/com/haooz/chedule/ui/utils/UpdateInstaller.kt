package com.haooz.chedule.ui.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.haooz.chedule.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 应用更新：下载与安装的公共入口，弹窗与设置页共用 */
internal object UpdateInstaller {

    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun apkFile(context: Context, tag: String): File =
        File(context.filesDir, "update-$tag.apk")

    fun hasValidApk(context: Context, tag: String): Boolean {
        val file = apkFile(context, tag)
        return file.exists() && file.length() > 0
    }

    /**
     * 下载 APK 到 filesDir。
     * @param onProgress 0f..1f，在主线程回调
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        tag: String,
        onProgress: suspend (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (apkUrl.isBlank()) throw IllegalArgumentException("未找到下载链接")
        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.connect()
        val fileSize = connection.contentLength.toLong()
        val file = apkFile(context, tag)
        connection.inputStream.use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (fileSize > 0) {
                        val p = (totalRead.toFloat() / fileSize).coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) { onProgress(p) }
                    }
                }
            }
        }
        file
    }

    /**
     * 安装 APK：优先 Shizuku 静默安装，失败回退系统安装器。
     * @param onInstallingChanged 主线程回调安装中状态
     * @param onFinished 主线程回调结束（静默成功/失败回退系统安装器/系统安装器已拉起）
     */
    fun installApk(
        context: Context,
        file: File,
        onInstallingChanged: (Boolean) -> Unit,
        onFinished: (() -> Unit)? = null,
    ) {
        if (ShizukuManager.isShizukuRunning() && ShizukuManager.checkSelfPermission()) {
            onInstallingChanged(true)
            installScope.launch {
                val (ok, message) = withContext(Dispatchers.IO) {
                    ShizukuManager.silentInstallApk(file.absolutePath)
                }
                onInstallingChanged(false)
                if (ok) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    onFinished?.invoke()
                } else {
                    Toast.makeText(context, "静默安装失败，已改用系统安装器", Toast.LENGTH_SHORT).show()
                    onInstallingChanged(true)
                    launchSystemInstaller(context, file)
                    onFinished?.invoke()
                }
            }
        } else {
            onInstallingChanged(true)
            launchSystemInstaller(context, file)
            onFinished?.invoke()
        }
    }

    private fun launchSystemInstaller(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
