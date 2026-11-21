package com.haooz.chedule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.backup.encodeBackupJson
import com.haooz.chedule.data.backup.parseFullScheduleJson
import com.haooz.chedule.data.mcp.CloudMcpBridge
import com.haooz.chedule.data.mcp.CloudMcpConfig
import com.haooz.chedule.data.mcp.CloudMcpDownloadPreview
import com.haooz.chedule.data.mcp.CloudMcpStatus
import com.haooz.chedule.data.mcp.CloudMcpUploadPreview
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.i18n.McpStr
import com.haooz.chedule.ui.components.PrivateSettingField
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.screens.applyScheduleData
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.theme.NexioTheme

/** 独立可选扩展；打开页面不会联网或上传，所有外发由明确按钮触发。 */
@Composable
fun CloudMcpSection(
    onCopyToClipboard: ((String) -> Unit)? = null,
    courseViewModel: CourseViewModel = CourseViewModel(),
    scheduleViewModel: ScheduleViewModel = ScheduleViewModel(),
    settingsViewModel: SettingsViewModel = SettingsViewModel(),
) {
    val controller = CloudMcpBridge.controller
    if (!controller.supported) return

    val theme = NexioTheme.colorScheme
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<CloudMcpStatus?>(null) }
    var preview by remember { mutableStateOf<CloudMcpUploadPreview?>(null) }
    var uploadConfig by remember { mutableStateOf<CloudMcpConfig?>(null) }
    var download by remember { mutableStateOf<CloudMcpDownloadPreview?>(null) }
    var downloadConfig by remember { mutableStateOf<CloudMcpConfig?>(null) }

    LaunchedEffect(controller) {
        try {
            val saved = controller.load()
            url = saved.url
            token = saved.token
            enabled = saved.enabled
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            message = McpStr.loadFailed()
        } finally {
            loading = false
        }
    }

    fun invalidate() {
        dirty = true
        status = null
        preview = null
        uploadConfig = null
        download = null
        downloadConfig = null
        message = null
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                preview = null
                uploadConfig = null
                download = null
                downloadConfig = null
                message = McpStr.operationFailed()
            } finally {
                busy = false
            }
        }
    }

    SmallTitle(text = McpStr.cloudSection())
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                checked = enabled,
                onCheckedChange = { enabled = it; invalidate() },
                title = McpStr.cloudEnable(),
                summary = McpStr.cloudEnableHint(),
                enabled = !loading && !busy,
            )
            CloudDivider()
            PrivateSettingField(
                label = McpStr.cloudAddress(),
                value = url,
                onValueChange = { url = it; invalidate() },
                hint = "https://…/mcp",
                enabled = !loading && !busy,
                isAddress = true,
                onCopy = onCopyToClipboard,
            )
            CloudDivider()
            PrivateSettingField(
                label = McpStr.cloudToken(),
                value = token,
                onValueChange = { token = it; invalidate() },
                hint = McpStr.tokenLabel(),
                enabled = !loading && !busy,
                onCopy = onCopyToClipboard,
            )
            Text(
                text = when {
                    dirty -> McpStr.unsaved()
                    message != null -> message.orEmpty()
                    status != null -> status!!.let {
                        McpStr.cloudReady(
                            it.revision,
                            if (it.hasSchedule) McpStr.hasSchedule() else McpStr.emptySchedule(),
                        )
                    }
                    else -> McpStr.cloudIdle()
                },
                style = NexioTheme.textStyles.footnote2,
                fontSize = 13.sp,
                color = theme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlatTextButton(
                    text = McpStr.save(),
                    enabled = !loading && !busy,
                    onClick = {
                        val config = CloudMcpConfig(url.trim(), token.trim(), enabled)
                        runAction {
                            controller.save(config)
                            dirty = false
                            preview = null
                            uploadConfig = null
                            message = McpStr.saved()
                        }
                    },
                    minWidth = 64.dp,
                    minHeight = 36.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    cornerRadius = 100.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                FlatTextButton(
                    text = if (busy) McpStr.working() else McpStr.test(),
                    enabled = enabled && !loading && !busy && !dirty && url.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        val config = CloudMcpConfig(url, token, enabled)
                        runAction { status = controller.inspect(config) }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(
                        color = Color(0xFF0A84FF),
                        textColor = Color.White,
                    ),
                    cornerRadius = 100.dp,
                    minWidth = 76.dp,
                    minHeight = 36.dp,
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                )
            }
            Text(
                text = McpStr.cloudPrivacy(),
                style = NexioTheme.textStyles.footnote2,
                fontSize = 13.sp,
                color = theme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
            )
            CloudDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = McpStr.upload(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.onSurface,
                    )
                    Text(
                        text = McpStr.uploadHint(),
                        style = NexioTheme.textStyles.footnote2,
                        fontSize = 13.sp,
                        color = theme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FlatTextButton(
                    text = if (busy) McpStr.working() else McpStr.uploadAction(),
                    enabled = enabled && !dirty && !busy && !loading && url.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        val config = CloudMcpConfig(url, token, enabled)
                        runAction {
                            val repo = CourseRepository()
                            val name = repo.getCurrentScheduleId()
                            val payload = buildShareScheduleMap(repo, name) ?: error("Empty schedule")
                            uploadConfig = config
                            preview = controller.previewUpload(config, encodeBackupJson(payload))
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(
                        color = Color(0xFF0A84FF),
                        textColor = Color.White,
                    ),
                    cornerRadius = 100.dp,
                    minWidth = 76.dp,
                    minHeight = 36.dp,
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                )
            }
            preview?.let { pending ->
                CloudDivider()
                Text(
                    text = McpStr.uploadPreview(
                        pending.recordCount,
                        if (pending.replacesExisting) McpStr.replaces() else "",
                        pending.baseRevision,
                    ),
                    fontSize = 14.sp,
                    color = theme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FlatTextButton(
                        text = McpStr.cancel(),
                        onClick = { preview = null; uploadConfig = null },
                        enabled = !busy,
                        minWidth = 64.dp,
                        minHeight = 36.dp,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        cornerRadius = 100.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FlatTextButton(
                        text = McpStr.confirmUpload(),
                        enabled = !busy,
                        onClick = {
                            val config = uploadConfig ?: return@FlatTextButton
                            runAction {
                                status = controller.confirmUpload(config, pending)
                                preview = null
                                uploadConfig = null
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(
                            color = Color(0xFF0A84FF),
                            textColor = Color.White,
                        ),
                        cornerRadius = 100.dp,
                        minWidth = 76.dp,
                        minHeight = 36.dp,
                        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(SECTION_GAP))
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = McpStr.download(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.onSurface,
                    )
                    Text(
                        text = McpStr.downloadHint(),
                        style = NexioTheme.textStyles.footnote2,
                        fontSize = 13.sp,
                        color = theme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FlatTextButton(
                    text = if (busy) McpStr.working() else McpStr.downloadAction(),
                    enabled = enabled && !busy && !loading && url.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        val config = CloudMcpConfig(url, token, enabled)
                        runAction {
                            downloadConfig = config
                            download = controller.previewDownload(config)
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(
                        color = Color(0xFF0A84FF),
                        textColor = Color.White,
                    ),
                    cornerRadius = 100.dp,
                    minWidth = 76.dp,
                    minHeight = 36.dp,
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                )
            }
            download?.let { pending ->
                CloudDivider()
                Text(
                    text = McpStr.downloadPreview(pending.scheduleName, pending.recordCount, pending.revision),
                    fontSize = 14.sp,
                    color = theme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FlatTextButton(
                        text = McpStr.cancel(),
                        onClick = { download = null; downloadConfig = null },
                        enabled = !busy,
                        minWidth = 64.dp,
                        minHeight = 36.dp,
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        cornerRadius = 100.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FlatTextButton(
                        text = McpStr.downloadConfirm(),
                        enabled = !busy,
                        onClick = {
                            val config = downloadConfig ?: return@FlatTextButton
                            runAction {
                                val targetName = uniqueScheduleName(pending.scheduleName) {
                                    scheduleViewModel.scheduleNames.value
                                }
                                val (ok, resultMessage) = applyScheduleData(
                                    viewModel = courseViewModel,
                                    scheduleViewModel = scheduleViewModel,
                                    settingsViewModel = settingsViewModel,
                                    scheduleName = targetName,
                                    data = parseCloudSchedule(pending.content),
                                )
                                check(ok) { resultMessage }
                                download = null
                                downloadConfig = null
                                message = McpStr.downloadDone(targetName)
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(
                            color = Color(0xFF0A84FF),
                            textColor = Color.White,
                        ),
                        cornerRadius = 100.dp,
                        minWidth = 76.dp,
                        minHeight = 36.dp,
                        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudDivider() {
    val theme = NexioTheme.colorScheme
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = theme.onSurfaceVariantActions.copy(alpha = 0.1f),
    )
}

/** 解析云端导出的分享/导出同构 JSON；失败由调用方按操作失败统一提示。 */
private fun parseCloudSchedule(content: String): Map<String, Any> {
    val (ok, message, data) = parseFullScheduleJson(content)
    check(ok) { message }
    return data ?: error(message)
}

/** 预览里的课表名落库时不撞已有表：重名自动加「 · 2」「 · 3」后缀。 */
private fun uniqueScheduleName(base: String, existing: () -> List<String>): String {
    val taken = existing().toSet()
    if (base !in taken) return base
    var index = 2
    while ("$base · $index" in taken) index++
    return "$base · $index"
}

/** 卡片之间的间距 */
private val SECTION_GAP = 12.dp
