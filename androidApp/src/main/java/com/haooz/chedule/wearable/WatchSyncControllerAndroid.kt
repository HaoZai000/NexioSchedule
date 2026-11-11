package com.haooz.chedule.wearable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.ui.components.WatchSyncController
import com.haooz.chedule.ui.components.WatchTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把 [BleScheduleSender] 包装成 `ui-shared` 认识的 [WatchSyncController]。
 *
 * 权限交给调用现场申请：扫描前没授权就先弹系统对话框，用户同意后**这一次**继续，
 * 拒绝则抛错由面板显示文案——不做二次解释弹窗，用户再点一次系统还会问。
 *
 * 写法与 `rememberCalendarSyncAction` 一致：用 [CompletableDeferred] 把异步的权限回调接回协程，
 * 这样面板里的 `scope.launch { scan() }` 可以原地等结果。
 */
@Composable
fun rememberWatchSyncController(): WatchSyncController {
    val context = LocalContext.current
    val sender = remember(context) { BleScheduleSender(context) }
    // 同一时刻只会有一个权限请求，用单槽持有待回调的 deferred
    val pending = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        pending[0]?.complete(grants.values.all { it })
        pending[0] = null
    }

    return remember(sender, launcher) {
        object : WatchSyncController {

            override suspend fun scan(timeoutMs: Long): List<WatchTarget> {
                ensurePermission()
                return sender.scan(timeoutMs).map { WatchTarget(it.name, it.address) }
            }

            override suspend fun send(target: WatchTarget, scheduleName: String): Result<Unit> {
                ensurePermission()
                return sender.send(WatchDevice(target.name, target.address), scheduleName)
            }

            override fun stopScan() = sender.stopScan()

            /** 没授权就就地申请，等到用户做出选择 */
            private suspend fun ensurePermission() {
                if (BleScheduleSender.hasPermissions(context)) return
                val deferred = CompletableDeferred<Boolean>()
                pending[0] = deferred
                withContext(Dispatchers.Main) {
                    launcher.launch(BleScheduleSender.requiredPermissions())
                }
                if (!deferred.await()) {
                    throw IllegalStateException(BackupStr.watchSyncPermissionDenied())
                }
            }
        }
    }
}
