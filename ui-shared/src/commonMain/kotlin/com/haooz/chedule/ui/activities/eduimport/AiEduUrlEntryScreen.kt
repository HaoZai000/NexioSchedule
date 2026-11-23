package com.haooz.chedule.ui.activities.eduimport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.platform.showToast
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.SchoolRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.i18n.CommonStr
import com.haooz.chedule.i18n.ImportStr
import com.haooz.chedule.ui.ai.AiEduUrlHolder
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.components.pageTopExtra
import com.haooz.chedule.ui.web.EduImportSelection
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.overlaydialog.LocalOverlayDialogBackdrop
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.textfield.TextField
import com.haooz.nexioui.text.NexioText as Text
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.topappbar.CollapsibleTopAppBarDefaults
import com.haooz.nexioui.topappbar.collapsibleTopInset
import com.haooz.nexioui.window.isTabletWidth

private const val PREFS_AI_EDU = "ai_edu_import_prefs"
private const val KEY_LAST_URL = "last_url"

/**
 * AI 教务导入第一步：输入教务网址。
 *
 * 平板内嵌在设置右栏（不进全屏白名单），确定地址后：
 * - 本地已有适配 → 弹适配器选择，走现成导入；
 * - 否则把地址交给 [AiEduUrlHolder]，跳全屏 AI 教务导入网页。
 */
@Composable
fun AiEduUrlEntryScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    /** 地址命中本地适配、用户选现成脚本后跳网页导入 */
    onOpenAdapterImport: () -> Unit,
    /** 地址确定，跳全屏 AI 教务导入网页 */
    onOpenWeb: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var urlInput by remember {
        mutableStateOf(AppStorage.store(PREFS_AI_EDU).getStringOrNull(KEY_LAST_URL).orEmpty())
    }
    val localSchools = remember { SchoolRepository().getSchools() }
    var matchedSchool by remember { mutableStateOf<SchoolData?>(null) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    fun commitUrl(url: String) {
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        AiEduUrlHolder.set(url)
        onOpenWeb()
    }

    fun onOpen() {
        val normalized = normalizeEduUrl(urlInput)
        if (normalized == null) {
            showToast(ImportStr.aiEduNeedUrl())
            return
        }
        AppStorage.store(PREFS_AI_EDU).edit { putString(KEY_LAST_URL, normalized) }
        val matched = findLocalSchoolForUrl(normalized, localSchools)
        if (matched == null) {
            commitUrl(normalized)
        } else {
            pendingUrl = normalized
            matchedSchool = matched
        }
    }

    DocumentPageScaffold(
        title = ImportStr.aiEduImportTitle(),
        onBack = onBack,
        endAction = { backdropAlpha, shadowAlpha, backdrop ->
            com.haooz.nexioui.iconbutton.IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onOpenHistory()
                },
                backdrop = backdrop,
                icon = AiEduHistoryIcon,
                contentDescription = ImportStr.aiEduHistory(),
                iconSize = 22.dp,
                backdropAlpha = backdropAlpha,
                shadowAlpha = shadowAlpha,
                performHapticFeedback = false,
            )
        },
    ) { scrollBehavior, backdrop ->
        val isTablet = isTabletWidth()
        val horizontalPadding = if (isTablet) 20.dp else 16.dp
        val scrollBehaviorModifier = remember(scrollBehavior) {
            Modifier
                .collapsibleTopInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        }
        Scaffold(topBar = {}) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexioTheme.colorScheme.surface)
                    .then(scrollBehaviorModifier)
                    .padding(horizontal = horizontalPadding)
                    .padding(
                        top = paddingValues.calculateTopPadding() +
                            CollapsibleTopAppBarDefaults.CollapsedHeight +
                            pageTopExtra(isTablet, hasSmallTitle = false),
                    ),
            ) {
                Text(
                    text = ImportStr.aiEduUrlHint(),
                    style = NexioTheme.textStyles.body2,
                    color = NexioTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    minLines = 4,
                    maxLines = 4,
                    label = ImportStr.aiEduUrlLabel(),
                    useLabelAsPlaceholder = true,
                )
                Spacer(Modifier.height(12.dp))
                FlatTextButton(
                    text = ImportStr.aiEduOpen(),
                    onClick = { onOpen() },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        AdapterSelectionBottomSheet(
            show = matchedSchool != null,
            school = matchedSchool,
            liquidGlassBackdrop = LocalOverlayDialogBackdrop.current ?: backdrop,
            onDismissRequest = {
                matchedSchool = null
                pendingUrl = null
            },
            onAdapterSelected = { school, adapter ->
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                matchedSchool = null
                pendingUrl = null
                EduImportSelection.set(school, adapter)
                onOpenAdapterImport()
            },
            footerContent = {
                FlatTextButton(
                    text = ImportStr.aiEduContinue(),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        val url = pendingUrl
                        matchedSchool = null
                        pendingUrl = null
                        if (url != null) commitUrl(url)
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}
