package com.haooz.chedule.ui.utils

import android.content.Context
import android.content.Intent
import com.haooz.chedule.data.NexioLog
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

    private const val TAG = "UpdateInstaller"

    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun apkFile(context: Context, tag: String): File =
        File(context.filesDir, "update-$tag.apk")

    private fun partFile(context: Context, tag: String): File =
        File(context.filesDir, "update-$tag.apk.part")

    /**
     * 本地是否已有一份**可安装**的安装包。
     *
     * 校验不通过时直接把文件删掉，避免损坏包被反复复用——
     * 这正是「安装报解析失败后，应用一直强制使用这个损坏包」的根因。
     */
    fun hasValidApk(context: Context, tag: String): Boolean {
        val file = apkFile(context, tag)
        val check = UpdateChecker.verifyApk(context, file, UpdateChecker.rememberedApkSha256(context, tag))
        if (!check.ok) {
            if (file.exists()) {
                NexioLog.w(TAG, "本地APK无效，已删除重下: $tag 原因=${check.reason}")
                runCatching { file.delete() }
            }
            return false
        }
        return true
    }

    /**
     * 下载 APK：先写 .part，通过完整性校验后再原子 rename。
     *
     * 校验链见 [UpdateChecker.verifyApk]；任何一步失败都会删掉 .part，
     * 保证不会残留半成品被当成可安装包。
     *
     * @param onProgress 0f..1f，在主线程回调
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        tag: String,
        onProgress: suspend (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (apkUrl.isBlank()) throw IllegalArgumentException("未找到下载链接")
        val finalFile = apkFile(context, tag)
        val part = partFile(context, tag)
        if (part.exists()) part.delete()
        try {
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            // 重定向到 CDN 时保持跟随，且不回退到缓存副本
            connection.instanceFollowRedirects = true
            connection.connect()
            val fileSize = connection.contentLength.toLong()
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
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
                    output.fd.sync()
                }
            }
            if (fileSize > 0 && part.length() != fileSize) {
                part.delete()
                throw java.io.IOException("APK 下载不完整: ${part.length()}/$fileSize")
            }
            if (fileSize <= 0) {
                // 未声明长度（如分块传输）时无法用体积判定，交给完整校验
                NexioLog.w(TAG, "服务端未声明长度，改用解析级校验: $tag")
            }

            // 优先用服务端下发的 SHA-256 做字节级校验，没有则退回解析级校验
            val expectedSha = UpdateChecker.rememberedApkSha256(context, tag)
            val check = UpdateChecker.verifyApk(context, part, expectedSha)
            if (!check.ok) {
                part.delete()
                throw java.io.IOException("APK 校验失败：${check.reason}")
            }

            if (finalFile.exists()) finalFile.delete()
            if (!part.renameTo(finalFile)) {
                part.delete()
                throw java.io.IOException("APK 落盘失败")
            }

            // 记住服务端 SHA-256（若有），下次复用本地包时按字节级复核
            finalFile
        } catch (e: Exception) {
            runCatching { part.delete() }
            throw e
        }
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
                if (ok) {
                    onInstallingChanged(false)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    onFinished?.invoke()
                } else {
                    NexioLog.w(TAG, "静默安装失败，回退系统安装器: $message")
                    Toast.makeText(context, "静默安装失败，已改用系统安装器", Toast.LENGTH_SHORT).show()
                    // 关键：先解除 installing 状态再拉起安装器。
                    // 否则用户在系统安装器里点取消后，弹窗会永久停在「安装中」且无法关闭。
                    onInstallingChanged(false)
                    onFinished?.invoke()
                    launchSystemInstaller(context, file)
                }
            }
        } else {
            // 系统安装器是外部 Activity，会被用户随时取消：
            // 拉起后立刻交还 UI 状态，避免弹窗卡在「安装中」
            launchSystemInstaller(context, file)
            onInstallingChanged(false)
            onFinished?.invoke()
        }
    }

    private fun launchSystemInstaller(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
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
