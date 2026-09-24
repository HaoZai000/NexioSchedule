/** 数据管理壳页面：课表导入 / 导出 / 备份（按 mode 分区） */
package com.haooz.chedule.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.haooz.chedule.ui.theme.CourseScheduleTheme

open class BackupAndMigrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()

        val mode = intent.getStringExtra(EXTRA_MODE)?.let { raw ->
            ScheduleDataManageMode.entries.firstOrNull { it.name == raw }
        } ?: ScheduleDataManageMode.Import
        val title = when (mode) {
            ScheduleDataManageMode.Import -> "课表导入"
            ScheduleDataManageMode.Export -> "课表导出"
            ScheduleDataManageMode.Backup -> "课表备份"
        }

        setContent {
            CourseScheduleTheme {
            val backgroundColor = MiuixTheme.colorScheme.surface
            val backdrop = rememberLayerBackdrop {
                drawRect(backgroundColor)
                drawContent()
            }
            val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            val scrollBehavior = rememberSharedScrollBehavior()

            val courseViewModel: CourseViewModel = viewModel()
            val scheduleViewModel: ScheduleViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel()

            Scaffold(
                topBar = {
                    ProgressiveBlurTopBar(
                        backdrop = liquidGlassBackdrop,
                    ) {
                        CollapsibleTopAppBar(
                            title = title,
                            largeTitle = title,
                            modifier = Modifier,
                            scrollBehavior = scrollBehavior,
                            contentPadding = {},
                            startAction = { backdropAlpha, shadowAlpha ->
                                LiquidTopBarButton(
                                    onClick = { finish() },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.ChevronBackward,
                                    contentDescription = "返回",
                                    performHapticFeedback = false,
                                    iconSize = 25.dp,
                                    iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            },
                        )
                    }
                }
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().then(
                            Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                        )
                    ) {
                        BackupAndMigrationScreen(
                            scrollBehavior = scrollBehavior,
                            courseViewModel = courseViewModel,
                            scheduleViewModel = scheduleViewModel,
                            settingsViewModel = settingsViewModel,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            mode = mode,
                        )
                    }
                }
            }
        
        }
        }
    }

    companion object {
        internal const val EXTRA_MODE = "mode"

        fun importIntent(context: Context): Intent =
            Intent(context, ScheduleImportActivity::class.java)
                .putExtra(EXTRA_MODE, ScheduleDataManageMode.Import.name)

        fun exportIntent(context: Context): Intent =
            Intent(context, ScheduleExportActivity::class.java)
                .putExtra(EXTRA_MODE, ScheduleDataManageMode.Export.name)

        fun backupIntent(context: Context): Intent =
            Intent(context, ScheduleBackupActivity::class.java)
                .putExtra(EXTRA_MODE, ScheduleDataManageMode.Backup.name)
    }
}

/** 课表导入壳页（独立类名，便于设置页按压态） */
class ScheduleImportActivity : BackupAndMigrationActivity()

/** 课表导出壳页 */
class ScheduleExportActivity : BackupAndMigrationActivity()

/** 课表备份壳页 */
class ScheduleBackupActivity : BackupAndMigrationActivity()
