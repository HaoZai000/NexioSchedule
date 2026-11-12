package com.haooz.chedule.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.BackupStr
import com.haooz.chedule.ui.components.text.miuix.Text
import com.haooz.chedule.ui.utils.screenWidthDp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.BlurBottomSheet
import top.yukonga.miuix.kmp.overlay.BlurBottomSheetTablet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
 * 「导出到安卓手表」弹窗：扫一轮 BLE 广播，列出附近的 Nexio 手表，点一个就推整表。
 *
 * 内容装在一张固定高度的卡片里：上半区随状态切换（搜索 / 空态 / 列表滚动），
 * 「重新搜索」贴卡片底部常驻，不随列表滚动。本组件应由父级常驻挂载、用 [show] 驱动，
 * 否则发送途中切走会让协程随组合销毁而中断。
 */
@Composable
fun WatchSyncSheet(
    show: Boolean,
    scheduleName: String,
    controller: WatchSyncController,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isTablet = screenWidthDp() >= 600

    var scanning by remember { mutableStateOf(false) }
    var targets by remember { mutableStateOf(emptyList<WatchTarget>()) }
    var sendingTarget by remember { mutableStateOf<WatchTarget?>(null) }
    var error by remember { mutableStateOf("") }
    var sentAddresses by remember { mutableStateOf(emptySet<String>()) }
    var lastSentName by remember { mutableStateOf("") }

    fun applyScanResult(result: Result<List<WatchTarget>>) {
        scanning = false
        result
            .onSuccess { targets = it }
            .onFailure { error = it.message ?: BackupStr.watchSyncScanFailed() }
    }

    fun startScan() {
        if (scanning || sendingTarget != null) return
        error = ""
        sentAddresses = emptySet()
        lastSentName = ""
        targets = emptyList()
        scanning = true
        scope.launch { applyScanResult(runCatching { controller.scan() }) }
    }

    LaunchedEffect(show) {
        if (!show) {
            controller.stopScan()
            return@LaunchedEffect
        }
        error = ""
        sentAddresses = emptySet()
        lastSentName = ""
        sendingTarget = null
        targets = emptyList()
        scanning = true
        applyScanResult(runCatching { controller.scan() })
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .sheetContentBottomInset(isTablet),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(if (isTablet) 56.dp else 58.dp))

            Text(
                text = if (scheduleName.isBlank()) {
                    BackupStr.watchSyncHint()
                } else {
                    BackupStr.watchSyncHintN(scheduleName)
                },
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTablet) 320.dp else 260.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            sendingTarget != null -> StatusBlock(BackupStr.watchSyncSendingN(sendingTarget!!.name))
                            scanning -> StatusBlock(BackupStr.watchSyncScanning())
                            targets.isEmpty() -> Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (lastSentName.isNotBlank()) {
                                    MessageBlock(BackupStr.watchSyncSuccessN(lastSentName), isError = false)
                                }
                                if (error.isNotBlank()) MessageBlock(error, isError = true)
                                Text(
                                    text = BackupStr.watchSyncEmpty(),
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            else -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .overScrollVertical()
                                    .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                            ) {
                                if (lastSentName.isNotBlank()) {
                                    MessageBlock(BackupStr.watchSyncSuccessN(lastSentName), isError = false)
                                }
                                if (error.isNotBlank()) MessageBlock(error, isError = true)
                                targets.forEachIndexed { index, target ->
                                    WatchRow(
                                        target = target,
                                        sent = target.address in sentAddresses,
                                        enabled = sendingTarget == null,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            error = ""
                                            lastSentName = ""
                                            sendingTarget = target
                                            scope.launch {
                                                val result = controller.send(target, scheduleName)
                                                sendingTarget = null
                                                result
                                                    .onSuccess {
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
                                    if (index != targets.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 16.dp),
                                            thickness = 0.5.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        text = BackupStr.watchSyncRescan(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            startScan()
                        },
                        enabled = !scanning && sendingTarget == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        textColor = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    val dismiss = {
        if (sendingTarget == null) {
            controller.stopScan()
            onDismiss()
        }
    }

    if (isTablet) {
        BlurBottomSheetTablet(
            show = show,
            title = BackupStr.watchSyncTitle(),
            dimBackground = true,
            onDismissRequest = dismiss,
        ) {
            content()
        }
    } else {
        BlurBottomSheet(
            show = show,
            title = BackupStr.watchSyncTitle(),
            dimBackground = true,
            onDismissRequest = dismiss,
        ) {
            content()
        }
    }
}

@Composable
private fun WatchRow(
    target: WatchTarget,
    sent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !sent, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = target.address,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = if (sent) BackupStr.watchSyncSentAction() else BackupStr.watchSyncSendAction(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (sent) {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            } else {
                MiuixTheme.colorScheme.primary
            },
        )
    }
}

@Composable
private fun StatusBlock(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MessageBlock(text: String, isError: Boolean) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
