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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.ui.basic.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.CollapsibleTopAppBarDefaults.CollapsedHeight
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ProgressiveBlurTopBar
import com.haooz.chedule.ui.basic.rememberSharedScrollBehavior
import com.haooz.chedule.ui.screens.SchoolSelectionScreen
import com.haooz.chedule.ui.screens.WebViewScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
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
                        prefs.edit { putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis()) }
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

    @SuppressLint("ConfigurationScreenWidthHeight")
    @Composable
    private fun EducationalImportApp() {
        val isUpdating by _isUpdating.collectAsState()
        val isChecking by _isChecking.collectAsState()
        val updateProgress by _updateProgress.collectAsState()
        val dataVersion by _dataVersion.collectAsState()

        val courseViewModel: CourseViewModel = viewModel()
        val scheduleViewModel: ScheduleViewModel = viewModel()
        val settingsViewModel: SettingsViewModel = viewModel()

        val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
        val currentScheduleName by scheduleViewModel.currentScheduleName.collectAsState()

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

        var searchQuery by remember { mutableStateOf("") }
        var searchExpanded by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableIntStateOf(0) }

        // 点击搜索框时收起标题栏
        LaunchedEffect(searchExpanded) {
            if (searchExpanded) {
                scrollBehavior.collapse()
            }
        }

        var isDesktopMode by remember { mutableStateOf(false) }
        var currentAssetJsPath by remember { mutableStateOf<String?>(null) }
        var executeImportAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var toggleDesktopModeAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        when (currentScreen) {
            "selection" -> {
                val isTablet = LocalConfiguration.current.screenWidthDp >= 600
                val tabletHorizontalPadding = if (isTablet) {
                    val screenWidthDp = LocalConfiguration.current.screenWidthDp
                    ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
                } else 0.dp
                val hapticFeedback = LocalHapticFeedback.current
                val displayTabs = listOf("学校导入", "通用工具")

                Scaffold { paddingValues ->
                    val topBarHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) {
                        (scrollBehavior.currentHeightPx).toDp()
                    }
                    val statusBarHeight = WindowInsets.statusBars
                        .asPaddingValues().calculateTopPadding()
                    val blurHeight = 80.dp + statusBarHeight + 120.dp

                    var searchBackdropAlpha by remember { mutableFloatStateOf(0f) }
                    var searchShadowAlpha by remember { mutableFloatStateOf(0f) }

                    Box(modifier = Modifier.fillMaxSize()) {
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
                                    modifier = Modifier.fillMaxSize(),
                                    isUpdating = isUpdating,
                                    isChecking = isChecking,
                                    updateProgress = updateProgress,
                                    dataVersion = dataVersion,
                                    isInFreeformWindow = isInFreeformWindow,
                                    scrollBehavior = scrollBehavior,
                                    searchQuery = searchQuery,
                                    selectedTab = selectedTab,
                                    topContentPadding = paddingValues.calculateTopPadding() + topBarHeightDp + 100.dp,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    onAdapterSelected = { school, adapter ->
                                        selectedSchool = school
                                        selectedAdapter = adapter
                                        currentScreen = "webview"
                                    }
                                )
                            }
                        }

                        var topBarBlurAlpha by remember { mutableStateOf(0f) }
                        ProgressiveBlurTopBar(
                            backdrop = liquidGlassBackdrop,
                            height = blurHeight,
                            blurAlpha = topBarBlurAlpha,
                        ) {
                            CollapsibleTopAppBar(
                                title = "选择学校",
                                largeTitle = "选择学校",
                                modifier = Modifier,
                                scrollBehavior = scrollBehavior,
                                contentPadding = {},
                                gradientMaskHeight = CollapsedHeight + 190.dp,
                                onAlphaChanged = { bd, sh ->
                                    searchBackdropAlpha = bd
                                    searchShadowAlpha = sh
                                    topBarBlurAlpha = bd
                                },
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
                                    if (isUpdating) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = (-6).dp, y = (-4).dp)
                                                .size(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                progress = if (isChecking) null else updateProgress,
                                            )
                                        }
                                    } else {
                                        LiquidTopBarButton(
                                            onClick = {
                                                forceUpdate(this@EducationalImportActivity)
                                            },
                                            backdrop = liquidGlassBackdrop,
                                            icon = MiuixIcons.Normal.Update,
                                            contentDescription = "更新",
                                            iconSize = 28.dp,
                                            backdropAlpha = backdropAlpha,
                                            shadowAlpha = shadowAlpha,
                                        )
                                    }
                                },
                            )
                        }

                        Column(
                            modifier = Modifier
                                .padding(top = paddingValues.calculateTopPadding() + topBarHeightDp)
                                .fillMaxWidth()
                        ) {
                            SearchBar(
                                modifier = Modifier.padding(
                                    top = 8.dp,
                                    bottom = 6.dp,
                                    start = 6.dp + tabletHorizontalPadding,
                                    end = 6.dp + tabletHorizontalPadding
                                ),
                                inputField = {
                                    InputField(
                                        query = searchQuery,
                                        onQueryChange = { searchQuery = it },
                                        onSearch = { searchExpanded = false },
                                        expanded = searchExpanded,
                                        onExpandedChange = { searchExpanded = it },
                                        label = "搜索学校",
                                        backdrop = liquidGlassBackdrop,
                                        backdropAlpha = searchBackdropAlpha,
                                        shadowAlpha = searchShadowAlpha,
                                    )
                                },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                actionIcon = MiuixIcons.Normal.Close,
                                onActionClick = {
                                    searchExpanded = false
                                    searchQuery = ""
                                },
                                backdrop = liquidGlassBackdrop,
                                backdropAlpha = searchBackdropAlpha,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 20.dp + tabletHorizontalPadding, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                displayTabs.forEachIndexed { index, tabName ->
                                    val isSelected = selectedTab == index
                                    Surface(
                                        modifier = Modifier
                                            .clip(ContinuousRoundedRectangle(20.dp))
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                selectedTab = index
                                            },
                                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 16.dp).height(35.dp).clip(ContinuousCapsule()),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = tabName,
                                                fontSize = 14.sp,
                                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                    }
                                }
                            }
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
                            scheduleNames = scheduleNames,
                            currentScheduleName = currentScheduleName,
                            onBack = {
                                selectedSchool = null
                                currentScreen = "selection"
                            },
                            onImportComplete = { courses ->
                                // AndroidBridge 已为导入课程设置 scheduleId（目标课表，空为当前课表）
                                val targetScheduleId = courses.firstOrNull()?.scheduleId?.takeIf { it.isNotEmpty() }
                                if (targetScheduleId != null && targetScheduleId != currentScheduleName) {
                                    scheduleViewModel.saveCoursesToSchedule(targetScheduleId, courses)
                                } else {
                                    courseViewModel.replaceCourses(courses)
                                }
                                scheduleViewModel.refreshScheduleList()
                                applyPresetTimeSlots(settingsViewModel, scheduleViewModel, targetScheduleId)
                                applyImportedScheduleConfig(courseViewModel)
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

    private fun applyPresetTimeSlots(
        settingsViewModel: SettingsViewModel,
        scheduleViewModel: ScheduleViewModel,
        targetScheduleId: String?
    ) {
        val prefs = getSharedPreferences("edu_import_prefs", MODE_PRIVATE)
        val timeSlotsJson = prefs.getString("preset_time_slots", null) ?: return
        try {
            val timeSlots = Gson().fromJson<List<Map<String, Any>>>(
                timeSlotsJson,
                object : TypeToken<List<Map<String, Any>>>() {}.type
            ) ?: return
            if (timeSlots.isEmpty()) return

            // 目标课表为空、或与当前课表相同：写在当前课表；否则写入指定目标课表
            val isTargetCurrent = targetScheduleId.isNullOrEmpty() ||
                targetScheduleId == scheduleViewModel.currentScheduleName.value

            val repository = if (isTargetCurrent) null else CourseRepository(this@EducationalImportActivity)

            // 划分边界与写入目标统一使用目标课表的节数配置
            val morningSections: Int
            val afternoonSections: Int
            val eveningSections: Int
            if (isTargetCurrent) {
                morningSections = settingsViewModel.morningSections.value
                afternoonSections = settingsViewModel.afternoonSections.value
                eveningSections = settingsViewModel.eveningSections.value
            } else {
                val sid = targetScheduleId!!
                morningSections = repository!!.getMorningSections(sid)
                afternoonSections = repository.getAfternoonSections(sid)
                eveningSections = repository.getEveningSections(sid)
            }

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

            if (isTargetCurrent) {
                if (morningTimes.isNotEmpty()) settingsViewModel.saveMorningTimes(morningTimes)
                if (afternoonTimes.isNotEmpty()) settingsViewModel.saveAfternoonTimes(afternoonTimes)
                if (eveningTimes.isNotEmpty()) settingsViewModel.saveEveningTimes(eveningTimes)
                // 同步覆盖当前课表绑定的时间配置，避免之后被旧配置盖回
                settingsViewModel.applyTimeImportToCurrentSchedule(
                    morningSections, afternoonSections, eveningSections,
                    morningTimes, afternoonTimes, eveningTimes
                )
            } else {
                val sid = targetScheduleId!!
                repository!!.savePeriodTimes("morning", morningTimes, sid)
                repository.savePeriodTimes("afternoon", afternoonTimes, sid)
                repository.savePeriodTimes("evening", eveningTimes, sid)
                repository.applyTimeImportToSchedule(
                    sid, morningSections, afternoonSections, eveningSections,
                    morningTimes, afternoonTimes, eveningTimes
                )
            }

            prefs.edit {remove("preset_time_slots")}
            Log.d("EduImport", "预设时间段应用成功(课表=${targetScheduleId ?: "当前"}): 上午${morningTimes.size}节, 下午${afternoonTimes.size}节, 晚上${eveningTimes.size}节")
        } catch (e: Exception) {
            Log.e("EduImport", "应用预设时间段失败: ${e.message}")
        }
    }

    /**
     * 应用教务脚本通过 saveCourseConfig 传回的课表配置（开学时间、总周数）。
     * 与 applyPresetTimeSlots 一致，仅在导入完成时消费一次性标记，避免重复/旧数据覆盖。
     */
    private fun applyImportedScheduleConfig(courseViewModel: CourseViewModel) {
        val prefs = getSharedPreferences("edu_import_prefs", MODE_PRIVATE)
        val startDate = prefs.getString("semester_start_date", null)
        val totalWeeks = prefs.getInt("semester_total_weeks", -1)

        var applied = false
        if (!startDate.isNullOrBlank()) {
            courseViewModel.setClassStartTime(startDate)
            applied = true
        }
        if (totalWeeks > 0) {
            courseViewModel.setTotalWeeks(totalWeeks)
            applied = true
        }
        if (applied) {
            prefs.edit { remove("semester_start_date"); remove("semester_total_weeks") }
            Log.d("EduImport", "应用课表配置成功: 开学时间=$startDate, 总周数=$totalWeeks")
        }
    }
}
