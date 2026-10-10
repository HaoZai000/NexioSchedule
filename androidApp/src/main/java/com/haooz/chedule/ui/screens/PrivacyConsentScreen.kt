package com.haooz.chedule.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首次启动隐私政策同意弹窗（合规：用户同意前不申请权限、不收集信息）。
 *
 * - 采用与应用内其他弹窗一致的 Miuix [OverlayDialog]，主界面在弹窗后可见；
 *   外层 [Scaffold] 必须用透明 containerColor，否则它的 Surface 会铺一层不透明底色把主界面盖住；
 *   触摸由弹窗自身的遮罩层拦截，无需额外的全屏触摸吞噬层；
 * - 「同意」先播放弹窗关闭动画（onDismissFinished），再进入主界面；
 *   「不同意」直接退出应用，不等动画（退出后动画无意义）；
 * - 合规要求必须由用户明确选择，故 onDismissRequest = null（点外部/返回键都不关闭），
 *   同时关掉预测性返回手势动画，避免手势把弹窗拖走却关不掉；
 * - 点击《隐私政策》在应用内打开全文页。
 */
@Composable
fun PrivacyConsentScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onOpenPolicy: () -> Unit,
) {
    // 关闭动画：先让弹窗播完退出动画，再执行实际动作（进入主界面）
    var dialogVisible by remember { mutableStateOf(true) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 透明 Scaffold：只为提供 LocalDialogStates + MiuixPopupHost，让弹窗能渲染在最上层
    Scaffold(containerColor = Color.Transparent) { _ ->
        OverlayDialog(
            title = "隐私政策",
            summary = "欢迎使用 Nexio 课程表。\n\n为提供课程表、课程提醒、天气等功能，我们需要在您同意后申请必要权限，并处理实现功能所必需的信息。\n您的课程数据默认仅保存在本机，设备信息仅用于匿名统计且不含身份信息。",
            show = dialogVisible,
            // 不可点击外部或返回键关闭，必须明确选择
            onDismissRequest = null,
            onDismissFinished = { pendingAction?.invoke() },
            // 不可返回关闭 → 不响应预测性返回手势动画
            enablePredictiveBackGesture = false,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "《Nexio 课程表隐私政策》",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPolicy() }
                        .padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    text = "同意并继续",
                    onClick = {
                        pendingAction = onAgree
                        dialogVisible = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = "不同意",
                    // 直接退出，不等弹窗关闭动画（退出后动画无意义，且会拖慢退出）
                    onClick = { onDecline() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}