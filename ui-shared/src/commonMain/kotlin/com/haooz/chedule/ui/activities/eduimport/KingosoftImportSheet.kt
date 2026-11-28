package com.haooz.chedule.ui.activities.eduimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.platform.ioDispatcher
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.sync.kingosoft.KingosoftScheduleSyncer
import com.haooz.chedule.data.sync.kingosoft.KingosoftSyncConfig
import com.haooz.chedule.data.sync.kingosoft.KingosoftSyncStore
import com.haooz.chedule.data.util.suspendRunCatching
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.SettingsStr
import com.haooz.chedule.ui.components.sheetContentBottomInset
import com.haooz.chedule.ui.components.text.material.Text
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.nexioui.blurbottomsheet.BlurBottomSheet
import com.haooz.nexioui.blurbottomsheet.BlurBottomSheetTablet
import com.haooz.nexioui.blurbottomsheet.LocalBlurBottomSheetContentExpanded
import com.haooz.nexioui.blurbottomsheet.LocalSheetContentBackdrop
import com.haooz.nexioui.blurbottomsheet.LocalSheetTopBarMaterial
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Close
import com.haooz.nexioui.icon.extended.Info
import com.haooz.nexioui.iconbutton.IconButton
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KINGOSOFT_ADB_EXTRACT_COMMAND = """
    adb shell "su -c 'cat /data/data/com.kingosoft.activity_kb_common/shared_prefs/android.PREFERENCE_FILE_KEY.xml'" | python3 -c '
    import json, sys, xml.etree.ElementTree as ET
    root = ET.fromstring(sys.stdin.read())
    d = json.loads(next(e.text for e in root if e.attrib.get("name") == "android.LOGIN.chack"))
    jw = next(i for i in d["info"] if i.get("os") == "jw")
    print("教务系统地址:", jw["url"])
    print("加密密钥:    ", jw["encrpt"])
    print("学号 / 工号: ", d["xqdlzh"])
    print("用户类型:    ", d["usertype"])
    '
""".trimIndent()

@Composable
internal fun KingosoftImportBottomSheet(
    show: Boolean,
    liquidGlassBackdrop: GlassBackdrop?,
    onDismissRequest: () -> Unit,
    onImported: (String) -> Unit,
) {
    val repository = remember { CourseRepository.getInstance() }
    val viewModel = courseViewModel()
    val scope = rememberCoroutineScope()
    val scheduleId by scheduleViewModel().currentScheduleName.collectAsState()
    val activeSync by KingosoftScheduleSyncer.activeSchedule.collectAsState()
    var initial by remember(scheduleId) { mutableStateOf(KingosoftSyncConfig()) }
    var draft by remember(scheduleId) { mutableStateOf(KingosoftSyncConfig()) }
    var error by remember(scheduleId) { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var copiedCommand by remember(showHelp) { mutableStateOf(false) }
    LaunchedEffect(show, scheduleId) {
        if (show) {
            val loaded = KingosoftSyncStore.getConfig(scheduleId)
            initial = loaded
            draft = loaded.copy(encrptKey = "")
            error = null
            showHelp = false
        } else {
            showHelp = false
        }
    }
    val clipboardManager = LocalClipboardManager.current
    val isBusy = syncing || activeSync == scheduleId

    val isTablet = screenWidthDp() >= 600
    val dismiss = { if (!syncing) onDismissRequest() }
    val startAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        IconButton(
            onClick = dismiss,
            enabled = !syncing,
            backdrop = LocalSheetContentBackdrop.current ?: requireNotNull(liquidGlassBackdrop),
            icon = NexioIcons.Normal.Close,
            contentDescription = CommonStr.close(),
            modifier = Modifier.padding(start = if (isTablet) 16.dp else 18.dp),
            iconSize = 24.dp,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }
    val endAction: @Composable () -> Unit = {
        val material = LocalSheetTopBarMaterial.current
        IconButton(
            onClick = { showHelp = true },
            backdrop = LocalSheetContentBackdrop.current ?: requireNotNull(liquidGlassBackdrop),
            icon = NexioIcons.Info,
            contentDescription = SettingsStr.kingosoftHelpTitle(),
            modifier = Modifier.padding(end = if (isTablet) 16.dp else 18.dp),
            iconSize = 24.dp,
            backdropAlpha = material.backdropAlpha,
            shadowAlpha = material.shadowAlpha,
        )
    }
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(if (LocalBlurBottomSheetContentExpanded.current) Modifier.fillMaxHeight() else Modifier)
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .sheetContentBottomInset(isTablet),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(if (isTablet) 56.dp else 58.dp))
            KingosoftConfigFields(draft, initial.encrptKey.isNotEmpty(), enabled = !isBusy) { draft = it }
            SwitchPreference(
                title = SettingsStr.kingosoftAutoSync(),
                checked = draft.autoSyncEnabled,
                enabled = !isBusy,
                onCheckedChange = { checked ->
                    if (checked == draft.autoSyncEnabled) return@SwitchPreference
                    draft = draft.copy(autoSyncEnabled = checked)
                },
            )
            error?.let { Text(text = it, color = NexioTheme.colorScheme.error) }
            FlatTextButton(
                text = if (isBusy) SettingsStr.kingosoftSyncing() else SettingsStr.kingosoftImportNow(),
                enabled = !isBusy,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (syncing || activeSync == scheduleId) return@FlatTextButton
                    syncing = true
                    error = null
                    val targetScheduleId = scheduleId
                    val submitted = draft
                    scope.launch {
                        try {
                            suspendRunCatching {
                                withContext(ioDispatcher) {
                                    val next = submitted.withStoredKey(KingosoftSyncStore.getConfig(targetScheduleId))
                                    KingosoftScheduleSyncer.importAndSync(targetScheduleId, next, repository)
                                }
                            }.onSuccess { outcome ->
                                viewModel.reloadCourses()
                                onDismissRequest()
                                onImported(outcome.summary)
                            }.onFailure {
                                error = SettingsStr.kingosoftSyncFailed(it.message ?: it.toString())
                            }
                        } finally {
                            syncing = false
                        }
                    }
                },
            )
        }
    }
    if (isTablet) {
        BlurBottomSheetTablet(show = show, title = SettingsStr.kingosoftImportTitle(), dimBackground = true,
            onDismissRequest = dismiss, startAction = startAction, endAction = endAction) { content() }
    } else {
        BlurBottomSheet(show = show, title = SettingsStr.kingosoftImportTitle(), dimBackground = true,
            onDismissRequest = dismiss, startAction = startAction, endAction = endAction) { content() }
    }
    OverlayDialog(
        title = SettingsStr.kingosoftHelpTitle(),
        summary = SettingsStr.kingosoftHelpSteps(),
        show = showHelp,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = { showHelp = false },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlatTextButton(
                text = if (copiedCommand) SettingsStr.kingosoftCommandCopied() else SettingsStr.kingosoftCopyAdbCommand(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(KINGOSOFT_ADB_EXTRACT_COMMAND))
                    copiedCommand = true
                },
                modifier = Modifier.weight(1f),
            )
            FlatTextButton(
                text = CommonStr.ok(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = { showHelp = false },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

internal fun KingosoftSyncConfig.withStoredKey(previous: KingosoftSyncConfig) = copy(
    jwBaseUrl = jwBaseUrl.trim(), userId = userId.trim(), userType = userType.trim(),
    encrptKey = encrptKey.ifEmpty { previous.encrptKey },
)

@Composable
internal fun KingosoftConfigFields(
    draft: KingosoftSyncConfig, hasStoredKey: Boolean, enabled: Boolean, onChange: (KingosoftSyncConfig) -> Unit,
) {
    TextField(value = draft.jwBaseUrl, onValueChange = { onChange(draft.copy(jwBaseUrl = it)) },
        label = SettingsStr.kingosoftBaseUrl(), enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextField(value = draft.encrptKey, onValueChange = { onChange(draft.copy(encrptKey = it)) },
        label = if (hasStoredKey) SettingsStr.kingosoftKeepKey() else SettingsStr.kingosoftKey(),
        enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextField(value = draft.userId, onValueChange = { onChange(draft.copy(userId = it)) },
        label = SettingsStr.kingosoftUserId(), enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextField(value = draft.userType, onValueChange = { onChange(draft.copy(userType = it)) },
        label = SettingsStr.kingosoftUserType(), enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
}
