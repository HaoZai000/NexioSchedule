package com.haooz.chedule.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.WidgetIntroScreen
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop

/**
 * 平板分栏右栏的「直达型分类」内容。
 *
 * 手机上「提醒 / 小组件 / 关于 / 捐赠」点一下直接压全屏路由；平板右栏不能压全屏页（分栏体验会断），
 * 所以这里把同一个页面的**内容**内嵌进右栏，顶栏标题仍由 MainActivity 的叠层标题渲染。
 *
 * ⚠ 四个页面都传 `scrollBehavior = null`：右栏没有折叠大标题，页面退化成普通滚动容器，
 *   与「更新 / 备份与同步 / 数据管理」三个内嵌页保持同一套约定。
 *
 * @param paneBackdrop 右栏**本地**采样层（由 `TabletSettingsScreen` 录制）。
 *   底部玻璃按钮必须采样它，不能采样 `host.liquidGlassBackdrop` —— 按钮本身就在那个全局
 *   玻璃层内部，采样自身会循环采样，表现为偶发花屏/黑块（旧 `TabletSettingsScreen` 同一约定）。
 */
@Composable
fun SettingsDirectPane(
    category: SettingsCategory,
    host: SettingsContentHost,
    paneBackdrop: GlassBackdrop,
) {
    when (category) {
        SettingsCategory.Reminder -> Box(modifier = Modifier.fillMaxSize()) {
            CourseReminderScreen(
                settingsViewModel = host.settingsViewModel,
                scrollBehavior = null,
                liquidGlassBackdrop = host.liquidGlassBackdrop,
                reminderController = host.directPlatform.reminderController,
            )
            // 底部「测试通知」浮层：与手机 CourseReminderRoute 的 overlay **同一规则** ——
            // 只要这一端有测试能力就画，不因「右栏没有全屏玻璃层」而少一颗按钮。
            val testAction = host.directPlatform.onSendTestNotification
            if (testAction != null) {
                LiquidGlassTextButton(
                    text = host.directPlatform.testActionLabel,
                    onClick = testAction,
                    // 兄弟节点层：录制内容不含按钮本身，采样安全
                    backdrop = paneBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 36.dp, end = 36.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp),
                )
            }
        }

        SettingsCategory.Widget -> WidgetIntroScreen(
            scrollBehavior = null,
            liquidGlassBackdrop = host.liquidGlassBackdrop,
            onRequestWidgetRefresh = host.directPlatform.onRequestWidgetRefresh,
        )

        // 捐赠：与手机同一个页面，右栏内嵌时去掉折叠顶栏（标题由设置页叠层画）
        SettingsCategory.Donate -> AppreciateAuthorScreen(scrollBehavior = null)

        SettingsCategory.About -> {
            // 内嵌时弹窗要采样到整屏（含左栏），所以另起一个本栏 backdrop 给弹窗用。
            // 注意别把参数 paneBackdrop 用在这里当「卡片」采样层：卡片本身就在该层内部，
            // 采样自身会循环采样 —— 这里刻意用空层（= 不做模糊），不要改成传 paneBackdrop。
            val aboutPaneBackdrop = rememberLayerBackdrop()
            AboutScreen(
                onBack = {},
                liquidGlassBackdrop = aboutPaneBackdrop,
                embedded = true,
                // 弹窗要采样到整屏（含左栏）：优先用宿主传进来的全屏玻璃层，
                // 没有材质档时退回本栏 backdrop（此时弹窗也不用玻璃）
                dialogBackdrop = host.liquidGlassBackdrop ?: aboutPaneBackdrop,
                onNavigate = host.onNavigate,
                appVersion = host.directPlatform.aboutVersion,
                appName = host.directPlatform.aboutName,
                appIcon = host.directPlatform.aboutIcon,
                appIconShape = host.directPlatform.appIconShape,
                onExitApp = host.directPlatform.onExitApp,
                diagnosticRecorder = host.directPlatform.diagnosticRecorder,
            )
        }

        else -> Unit
    }
}
