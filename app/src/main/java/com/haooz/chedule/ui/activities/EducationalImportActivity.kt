/** 教务系统导入页面 */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.ui.components.CollapsibleTopAppBar
import com.haooz.chedule.ui.components.rememberSharedScrollBehavior
import com.haooz.chedule.ui.effects.liquidglass.LiquidTopBarButton
import com.haooz.chedule.ui.effects.liquidglass.ProgressiveBlurTopBar
import com.haooz.chedule.ui.screens.SchoolSelectionScreen
import com.haooz.chedule.ui.screens.WebViewScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

class EducationalImportActivity : ComponentActivity() {

    var isInFreeformWindow by mutableStateOf(false)
        private set

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        isInFreeformWindow = isInMultiWindowMode
    }

    companion object {
        private const val TAG = "EduImport"
        private const val PREFS_NAME = "edu_import_prefs"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        private const val AUTO_UPDATE_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L

        private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _isUpdating = MutableStateFlow(false)
        private val _isChecking = MutableStateFlow(false)
        private val _updateProgress = MutableStateFlow(0f)
        private val _dataVersion = MutableStateFlow(0)
        private val _initialLoadDone = MutableStateFlow(false)

        @SuppressLint("UseKtx")
        fun startUpdate(context: android.content.Context) {
            if (_isUpdating.value || _isChecking.value) return

            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIME, 0)
            val now = System.currentTimeMillis()
            val needsUpdate = lastUpdateTime == 0L || (now - lastUpdateTime > AUTO_UPDATE_INTERVAL_MS)

            if (!needsUpdate) {
                _initialLoadDone.value = true
                return
            }

            _isChecking.value = true
            _isUpdating.value = true
            _updateProgress.value = 0f
            _initialLoadDone.value = false
            updateScope.launch {
                try {
                    ScriptRepository(context, ScriptRepository.getRepoUrl(context)).updateAll(
                        onLog = { },
                        onProgress = { progress ->
                            _updateProgress.value = progress
                            if (progress > 0.05f) _isChecking.value = false
                        }
                    )
                    prefs.edit().putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis()).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "更新失败: ${e.message}")
                } finally {
                    _isChecking.value = false
                    _isUpdating.value = false
                    _dataVersion.value++
                    _initialLoadDone.value = true
                }
            }
        }

        fun forceUpdate(context: android.content.Context) {
            if (_isUpdating.value || _isChecking.value) return
            _isChecking.value = true
            _isUpdating.value = true
            _updateProgress.value = 0f
            updateScope.launch {
                try {
                    val result = ScriptRepository(context, ScriptRepository.getRepoUrl(context)).updateAll(
                        onLog = { },
                        onProgress = { progress ->
                            _updateProgress.value = progress
                        }
                    )
                    val msg = when (result) {
                        0 -> "已是最新版本"
                        1 -> "更新完成"
                        -1 -> "更新失败，请检查网络连接"
                        else -> "更新失败"
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    if (result >= 0) {
                        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        prefs.edit().putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis()).apply()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "手动更新失败: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "更新失败", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    _isChecking.value = false
                    _isUpdating.value = false
                    _dataVersion.value++
                }
            }
        }
    }

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
        startUpdate(this)
        setContent {
            CourseScheduleTheme {
                EducationalImportApp()
            }
        }
    }

    @Composable
    private fun EducationalImportApp() {
        val isUpdating by _isUpdating.collectAsState()
        val isChecking by _isChecking.collectAsState()
        val updateProgress by _updateProgress.collectAsState()
        val dataVersion by _dataVersion.collectAsState()

        val courseViewModel: CourseViewModel = viewModel()
        val scheduleViewModel: ScheduleViewModel = viewModel()
        val settingsViewModel: SettingsViewModel = viewModel()

        val backgroundColor = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }
        val liquidGlassBackdrop: LayerBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
        val scrollBehavior = rememberSharedScrollBehavior()

        var currentScreen by remember { mutableStateOf("selection") }
        var selectedSchool by remember { mutableStateOf<SchoolData?>(null) }
        var selectedAdapter by remember { mutableStateOf<AdapterData?>(null) }

        var isDesktopMode by remember { mutableStateOf(false) }
        var currentAssetJsPath by remember { mutableStateOf<String?>(null) }
        var executeImportAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var toggleDesktopModeAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        when (currentScreen) {
            "selection" -> {
                Scaffold(
                    topBar = {
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop,
                        ) {
                            CollapsibleTopAppBar(
                                title = "选择学校",
                                largeTitle = "选择学校",
                                modifier = Modifier,
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                startAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = { finish() },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.ChevronBackward,
                                        contentDescription = "返回",
                                        iconSize = 25.dp,
                                        iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                                endAction = { backdropAlpha, shadowAlpha ->
                                    if (!isUpdating) {
                                        LiquidTopBarButton(
                                            onClick = {
                                                forceUpdate(this@EducationalImportActivity)
                                            },
                                            backdrop = liquidGlassBackdrop,
                                            icon = MiuixIcons.Normal.Update,
                                            contentDescription = "更新",
                                            iconSize = 25.dp,
                                            backdropAlpha = backdropAlpha,
                                            shadowAlpha = shadowAlpha,
                                        )
                                    }
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
                            SchoolSelectionScreen(
                                isUpdating = isUpdating,
                                isChecking = isChecking,
                                updateProgress = updateProgress,
                                dataVersion = dataVersion,
                                isInFreeformWindow = isInFreeformWindow,
                                scrollBehavior = scrollBehavior,
                                onSchoolSelected = { school, adapter ->
                                    selectedSchool = school
                                    selectedAdapter = adapter
                                    currentScreen = "webview"
                                }
                            )
                        }
                    }
                }
            }
            "webview" -> {
                val school = selectedSchool
                val adapter = selectedAdapter
                if (school != null && adapter != null) {
                    Box(Modifier.fillMaxSize()) {
                        WebViewScreen(
                            school = school,
                            adapterId = adapter.adapterId,
                            importUrl = adapter.importUrl,
                            assetJsPath = adapter.assetJsPath,
                            isLiquidGlass = true,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onBack = { currentScreen = "selection" },
                            onImportComplete = { courses ->
                                courseViewModel.replaceCourses(courses)
                                scheduleViewModel.refreshScheduleList()
                                applyPresetTimeSlots(settingsViewModel)
                                Toast.makeText(this@EducationalImportActivity, "课程已保存，共 ${courses.size} 门课程", Toast.LENGTH_SHORT).show()
                            },
                            onDesktopModeChanged = { isDesktopMode = it },
                            onAssetJsPathChanged = { currentAssetJsPath = it },
                            onExecuteImportRef = { action -> executeImportAction = action },
                            onToggleDesktopModeRef = { action -> toggleDesktopModeAction = action }
                        )
                    }
                }
            }
        }
    }

    private fun applyPresetTimeSlots(settingsViewModel: SettingsViewModel) {
        val prefs = getSharedPreferences("edu_import_prefs", MODE_PRIVATE)
        val timeSlotsJson = prefs.getString("preset_time_slots", null) ?: return
        try {
            val timeSlots = Gson().fromJson<List<Map<String, Any>>>(
                timeSlotsJson,
                object : TypeToken<List<Map<String, Any>>>() {}.type
            ) ?: return
            if (timeSlots.isEmpty()) return

            val morningSections = settingsViewModel.morningSections.value
            val afternoonSections = settingsViewModel.afternoonSections.value

            val morningTimes = mutableMapOf<Int, String>()
            val afternoonTimes = mutableMapOf<Int, String>()
            val eveningTimes = mutableMapOf<Int, String>()

            for (slot in timeSlots) {
                val number = (slot["number"] as? Number)?.toInt() ?: continue
                val startTime = slot["startTime"] as? String ?: ""
                val endTime = slot["endTime"] as? String ?: ""
                if (startTime.isEmpty() || endTime.isEmpty()) continue
                val timeStr = "$startTime-$endTime"

                when {
                    number <= morningSections -> morningTimes[number] = timeStr
                    number <= morningSections + afternoonSections -> afternoonTimes[number - morningSections] = timeStr
                    else -> eveningTimes[number - morningSections - afternoonSections] = timeStr
                }
            }

            if (morningTimes.isNotEmpty()) settingsViewModel.saveMorningTimes(morningTimes)
            if (afternoonTimes.isNotEmpty()) settingsViewModel.saveAfternoonTimes(afternoonTimes)
            if (eveningTimes.isNotEmpty()) settingsViewModel.saveEveningTimes(eveningTimes)

            prefs.edit().remove("preset_time_slots").apply()
            Log.d("EduImport", "预设时间段应用成功: 上午${morningTimes.size}节, 下午${afternoonTimes.size}节, 晚上${eveningTimes.size}节")
        } catch (e: Exception) {
            Log.e("EduImport", "应用预设时间段失败: ${e.message}")
        }
    }
}
