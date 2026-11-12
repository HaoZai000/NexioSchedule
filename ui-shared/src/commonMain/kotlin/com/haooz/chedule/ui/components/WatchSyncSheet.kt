package com.haooz.chedule.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.ui.components.text.miuix.Text
import com.haooz.chedule.ui.utils.screenHeightDp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 扫描到的手表（跨平台层只认名称与地址，不暴露平台类型） */
data class WatchTarget(val name: String, val address: String)

/**
 * 导出到手表的能力注入点。
 *
 * 蓝牙是 Android 专有能力，`ui-shared` 只负责界面，真正的扫描/传输由宿主实现。
 * 未注入时用 [NoopWatchSyncController]，弹窗会显示「当前平台不支持」而不是崩溃。
 */
interface WatchSyncController {
    suspend fun scan(timeoutMs: Long = 8_000L): List<WatchTarget>
    suspend fun send(target: WatchTarget, scheduleName: String): Result<Unit>
    fun stopScan()
}

/** 未注入时的占位实现（桌面端 / iOS / Harmony 等无蓝牙通道的平台） */
object NoopWatchSyncController : WatchSyncController {
    override suspend fun scan(timeoutMs: Long): List<WatchTarget> = emptyList()
    override suspend fun send(target: WatchTarget, scheduleName: String): Result<Unit> =
        Result.failure(IllegalStateException(BackupStr.watchSyncUnsupported()))

    override fun stopScan() = Unit
}

/**
 * 「导出到手表」弹窗：扫一轮 BLE 广播，列出附近的 Nexio 手表，点一个就推整表。
 *
 * 交互参照手机端其他底部面板：打开即自动扫，扫不到给明确原因（而不是空列表），
 * 发送中锁定列表并显示进度，成功后提示「手表端会自动刷新」。
 *
 * 注意：本组件应由父级常驻挂载、用 [show] 驱动，而不是 `if (show) Sheet()`。
 * 否则发送途中切走会让协程随组合销毁而中断，手表侧只收到半份数据。
 */
@Composable
fun WatchSyncSheet(
    show: Boolean,
    scheduleName: String,
    controller: WatchSyncController,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var targets by remember { mutableStateOf(emptyList<WatchTarget>()) }
    /** 正在发送的目标（null 表示空闲）；用整个对象而不是名字，避免同名设备混淆 */
    var sendingTarget by remember { mutableStateOf<WatchTarget?>(null) }
    var error by remember { mutableStateOf("") }
    /** 本次面板内已发送成功过的设备地址：行内直接显示「已发送」并禁点 */
    var sentAddresses by remember { mutableStateOf(emptySet<String>()) }
    var lastSentName by remember { mutableStateOf("") }

    fun resetSentState() {
        sentAddresses = emptySet()
        lastSentName = ""
    }

    fun rescan() {
        if (scanning || sendingTarget != null) return
        error = ""
        resetSentState()
        targets = emptyList()
        scanning = true
        scope.launch {
            val result = runCatching { controller.scan() }
            scanning = false
            result
                .onSuccess { targets = it }
                .onFailure { error = it.message ?: BackupStr.watchSyncScanFailed() }
        }
    }

    LaunchedEffect(show) {
        if (!show) {
            // 关掉面板就别再占用蓝牙扫描
            controller.stopScan()
            return@LaunchedEffect
        }
        // 每次打开都重新扫：手表可能刚打开应用，旧列表会误导
        error = ""
        resetSentState()
        sendingTarget = null
        targets = emptyList()
        scanning = true
        val result = runCatching { controller.scan() }
        scanning = false
        result
            .onSuccess { targets = it }
            .onFailure { error = it.message ?: BackupStr.watchSyncScanFailed() }
    }

    BlurBottomSheet(
        show = show,
        title = BackupStr.watchSyncTitle(),
        dimBackground = true,
        onDismissRequest = {
            if (sendingTarget == null) {
                controller.stopScan()
                onDismiss()
            }
        },
    ) {
        // 与其他底部面板保持一致：内容必须显式给高度（屏幕 52%）并自己滚动。
        // 不给高度时面板按内容量测，只会露出标题那一小条，看起来像「没展开」。
        // 顶部 56dp 是让开面板标题区，底部 inset 交给 sheetContentBottomInset 处理。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeightDp().dp * 0.52f)
                .verticalScroll(rememberScrollState())
                .sheetContentBottomInset(isTabletWidth())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(56.dp))

            Text(
                text = if (scheduleName.isBlank()) {
                    BackupStr.watchSyncHint()
                } else {
                    BackupStr.watchSyncHintN(scheduleName)
                },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            when {
                sendingTarget != null -> SendingBlock(sendingTarget!!.name)
                scanning -> SearchingBlock()
                else -> {
                    // 成功提示不顶掉列表：用户还要能看见「这台已发过」，也方便给多台手表各发一次
                    if (lastSentName.isNotBlank()) {
                        ResultBlock(
                            text = BackupStr.watchSyncSuccessN(lastSentName),
                            isError = false,
                        )
                    }
                    if (error.isNotBlank()) ResultBlock(text = error, isError = true)
                    if (targets.isEmpty()) {
                        Text(
                            text = BackupStr.watchSyncEmpty(),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp),
                        ) {
                            targets.forEachIndexed { index, target ->
                                WatchRow(
                                    target = target,
                                    sent = target.address in sentAddresses,
                                    enabled = sendingTarget == null,
                                    showDivider = index != targets.lastIndex,
                                    onClick = {
                                        error = ""
                                        lastSentName = ""
                                        sendingTarget = target
                                        scope.launch {
                                            val result = controller.send(target, scheduleName)
                                            sendingTarget = null
                                            result
                                                .onSuccess {
                                                    // 标记这台已发送，行内文案立刻变成「已发送」
                                                    sentAddresses = sentAddresses + target.address
                                                    lastSentName = target.name
                                                }
                                                .onFailure {
                                                    error = it.message
                                                        ?: BackupStr.watchSyncFailedGeneric()
                                                }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !scanning && sendingTarget == null) { rescan() }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = BackupStr.watchSyncRescan(),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchRow(
    target: WatchTarget,
    sent: Boolean,
    enabled: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 已发送过的设备不再可点：避免重复推同一份数据，也让状态一眼可见
                .clickable(enabled = enabled && !sent, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = target.name, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = target.address,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = if (sent) BackupStr.watchSyncSentAction() else BackupStr.watchSyncSendAction(),
                fontSize = 13.sp,
                color = if (sent) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.primary
                },
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun SearchingBlock() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            text = BackupStr.watchSyncScanning(),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun SendingBlock(deviceName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            text = BackupStr.watchSyncSendingN(deviceName),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ResultBlock(text: String, isError: Boolean) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (isError) {
            MiuixTheme.colorScheme.error
        } else {
            MiuixTheme.colorScheme.primary
        },
    )
}
