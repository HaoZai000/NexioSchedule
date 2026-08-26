package com.haooz.chedule.ui.activities

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import com.haooz.chedule.ui.basic.SharedScrollBehavior
import com.haooz.chedule.ui.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

@Composable
fun HolidaySettingsScreen(scrollBehavior: SharedScrollBehavior?, liquidGlassBackdrop: com.kyant.backdrop.Backdrop?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var entries by remember { mutableStateOf(HolidayManager.load(context, year)) }
    var loading by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogType by remember { mutableIntStateOf(HolidayManager.TYPE_HOLIDAY) }
    var date by remember { mutableStateOf("$year-01-01") }
    var name by remember { mutableStateOf("") }
    var followWeek by remember { mutableStateOf("1") }
    var followWeekday by remember { mutableStateOf("1") }
    var editingEntry by remember { mutableStateOf<HolidayManager.Entry?>(null) }
    var dateYear by remember { mutableIntStateOf(year) }
    var dateMonth by remember { mutableIntStateOf(1) }
    var dateDay by remember { mutableIntStateOf(1) }
    var endDateYear by remember { mutableIntStateOf(year) }
    var endDateMonth by remember { mutableIntStateOf(1) }
    var endDateDay by remember { mutableIntStateOf(1) }
    val currentDate = remember { LocalDate.now() }
    val canChooseYear = currentDate.monthValue == 12
    val listState = rememberLazyListState()
    val dropdownColors = top.yukonga.miuix.kmp.basic.DropdownDefaults.dropdownColors(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent
    )
    fun reload() { entries = HolidayManager.load(context, year) }
    fun requestYear(targetYear: Int, automatic: Boolean = false) {
        if (loading) return
        loading = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val conn = URL("https://unpkg.com/holiday-calendar@1.3.0/data/CN/$targetYear.json").openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                HolidayManager.parseApiResponse(text)
            }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                HolidayManager.mergeApiEntries(context, targetYear, result)
                if (targetYear == year) reload()
                loading = false
                if (!automatic) Toast.makeText(context, if (result.isEmpty()) "获取失败或暂无数据" else "已更新 ${result.size} 条记录", Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (HolidayManager.load(context, year).isEmpty()) requestYear(year, automatic = true)
    }
    Scaffold(topBar = {}) { padding ->
        val density = LocalDensity.current
        val topBarHeightDp = with(density) { (scrollBehavior?.currentHeightPx ?: 0f).toDp() }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().overScrollVertical().scrollEndHaptic(
                hapticFeedbackType = HapticFeedbackType.TextHandleMove
            ).then(scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + topBarHeightDp, 16.dp, 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SmallTitle(text = "数据管理", modifier = Modifier.offset((-15).dp))
                Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column {
                        val yearEntry = DropdownEntry((currentDate.year..currentDate.year + 1).map { selectedYear ->
                            DropdownItem(
                                text = selectedYear.toString(),
                                selected = selectedYear == year,
                                onClick = { year = selectedYear; reload() },
                            )
                        })
                        if (canChooseYear) {
                            OverlayDropdownMenu(
                                entry = yearEntry,
                                title = "年份",
                                summary = year.toString(),
                                collapseOnSelection = true,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = dropdownColors,
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(if (loading) "正在获取" else "获取网络数据", { requestYear(year) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                            TextButton("清空", { HolidayManager.clear(context, year); reload(); CourseReminderHelper.startReminderService(context) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(if (loading) "正在获取 ${year} 年数据" else "暂无 ${year} 年节假日数据", style = MiuixTheme.textStyles.body1)
                            Text(if (loading) "请稍候" else "可以获取网络数据，或使用下方入口手动添加", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                    }
                }
            }
            listOf(HolidayManager.TYPE_HOLIDAY to "节假日", HolidayManager.TYPE_WORKSWAP to "调休工作日").forEach { (type, title) ->
                val filtered = entries.filter { it.type == type }
                item { SmallTitle(text = title, modifier = Modifier.offset((-15).dp)) }
                item {
                    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        if (filtered.isEmpty()) {
                            Text("暂无$title", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(16.dp))
                        } else {
                            Column {
                                filtered.forEach { entry ->
                                    ArrowPreference(
                                        title = entry.name,
                                        summary = if (type == HolidayManager.TYPE_HOLIDAY) "${entry.date}${if (entry.endDate.isNotBlank()) " 至 ${entry.endDate}" else ""}" else {
                                            val weekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
                                            val mapping = if (entry.followWeek > 0 && entry.followWeekday in 1..7) "第${entry.followWeek}周${weekdays[entry.followWeekday - 1]}" else "待配置补班课程"
                                            "${entry.date} · $mapping"
                                        },
                                        onClick = {
                                            editingEntry = entry; dialogType = type; date = entry.date; name = entry.name
                                            runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { dateYear = it.year; dateMonth = it.monthValue; dateDay = it.dayOfMonth }
                                            val end = runCatching { LocalDate.parse(entry.endDate.ifBlank { entry.date }) }.getOrNull() ?: LocalDate.of(dateYear, dateMonth, dateDay)
                                            endDateYear = end.year; endDateMonth = end.monthValue; endDateDay = end.dayOfMonth
                                            followWeek = if (entry.followWeek > 0) entry.followWeek.toString() else "1"
                                            followWeekday = if (entry.followWeekday > 0) entry.followWeekday.toString() else "1"; showDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                        ArrowPreference(
                            title = if (type == HolidayManager.TYPE_HOLIDAY) "添加节假日" else "添加调休工作日",
                            summary = if (type == HolidayManager.TYPE_HOLIDAY) "当天不发送课程提醒" else "按指定周次和星期匹配课程",
                            onClick = {
                                if (type == HolidayManager.TYPE_HOLIDAY) {
                                    dialogType = HolidayManager.TYPE_HOLIDAY; date = "$year-01-01"; dateYear = year; dateMonth = 1; dateDay = 1; endDateYear = year; endDateMonth = 1; endDateDay = 1; name = ""; editingEntry = null; showDialog = true
                                } else {
                                    dialogType = HolidayManager.TYPE_WORKSWAP; date = "$year-01-01"; dateYear = year; dateMonth = 1; dateDay = 1; name = ""; followWeek = "1"; followWeekday = "1"; editingEntry = null; showDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showDialog) {
        top.yukonga.miuix.kmp.overlay.OverlayDialog(
            title = if (editingEntry == null) {
                if (dialogType == HolidayManager.TYPE_HOLIDAY) "添加节假日" else "添加调休工作日"
            } else "编辑${if (dialogType == HolidayManager.TYPE_HOLIDAY) "节假日" else "调休工作日"}",
            summary = null,
            show = true, liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("开始日期", style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Normal))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    NumberPicker(dateYear, { dateYear = it; date = "%04d-%02d-%02d".format(it, dateMonth, dateDay) }, range = 2024..2035, visibleItemCount = 3, itemHeight = 44.dp, textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), label = { "${it}年" }, modifier = Modifier.weight(1.35f))
                    NumberPicker(dateMonth, { dateMonth = it; val max = LocalDate.of(dateYear, it, 1).lengthOfMonth(); dateDay = dateDay.coerceAtMost(max); date = "%04d-%02d-%02d".format(dateYear, it, dateDay) }, range = 1..12, visibleItemCount = 3, itemHeight = 44.dp, textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), label = { "${it}月" }, modifier = Modifier.weight(0.9f))
                    val maxDay = LocalDate.of(dateYear, dateMonth, 1).lengthOfMonth()
                    NumberPicker(dateDay.coerceIn(1, maxDay), { dateDay = it; date = "%04d-%02d-%02d".format(dateYear, dateMonth, it) }, range = 1..maxDay, visibleItemCount = 3, itemHeight = 44.dp, textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), label = { "${it}日" }, modifier = Modifier.weight(0.9f))
                }
                if (dialogType == HolidayManager.TYPE_HOLIDAY) {
                    Text("结束日期", style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Normal))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        NumberPicker(endDateYear, { endDateYear = it }, range = 2024..2035, visibleItemCount = 3, itemHeight = 44.dp, textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), label = { "${it}年" }, modifier = Modifier.weight(1.35f))
                        NumberPicker(endDateMonth, { endDateMonth = it; endDateDay = endDateDay.coerceAtMost(LocalDate.of(endDateYear, it, 1).lengthOfMonth()) }, range = 1..12, visibleItemCount = 3, itemHeight = 44.dp, textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), label = { "${it}月" }, modifier = Modifier.weight(0.9f))
                        val endMaxDay = LocalDate.of(endDateYear, endDateMonth, 1).lengthOfMonth()
                        NumberPicker(endDateDay.coerceIn(1, endMaxDay), { endDateDay = it }, range = 1..endMaxDay, visibleItemCount = 3, itemHeight = 44.dp, textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), label = { "${it}日" }, modifier = Modifier.weight(0.9f))
                    }
                }
                NativeMiuixTextField(name, { name = it }, label = "名称", modifier = Modifier.fillMaxWidth())
                if (dialogType == HolidayManager.TYPE_WORKSWAP) {
                    Text("跟随课程")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NumberPicker(followWeek.toIntOrNull()?.coerceAtLeast(1) ?: 1, { followWeek = it.toString() }, range = 1..52, visibleItemCount = 3, itemHeight = 44.dp, textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), label = { "第${it}周" }, modifier = Modifier.weight(1f))
                        val weekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
                        NumberPicker(followWeekday.toIntOrNull()?.coerceIn(1, 7) ?: 1, { followWeekday = it.toString() }, range = 1..7, visibleItemCount = 3, itemHeight = 44.dp, textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), label = { weekdays[it - 1] }, modifier = Modifier.weight(1.2f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("保存", {
                    val valid = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
                    if (!valid) { Toast.makeText(context, "日期格式不正确", Toast.LENGTH_SHORT).show(); return@TextButton }
                    val all = HolidayManager.load(context, year).toMutableList()
                    editingEntry?.let { old -> all.removeAll { it.date == old.date && it.type == old.type && it.name == old.name } }
                    val week = followWeek.toIntOrNull()?.takeIf { it > 0 } ?: -1
                    val weekday = followWeekday.toIntOrNull()?.takeIf { it in 1..7 } ?: -1
                    val savedEndDate = if (dialogType == HolidayManager.TYPE_HOLIDAY) "%04d-%02d-%02d".format(endDateYear, endDateMonth, endDateDay) else ""
                    if (dialogType == HolidayManager.TYPE_HOLIDAY && savedEndDate < date) {
                        Toast.makeText(context, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    all += HolidayManager.Entry(date, endDate = savedEndDate, name = name.ifBlank { if (dialogType == HolidayManager.TYPE_HOLIDAY) "节假日" else "调休工作日" }, type = dialogType, followWeek = if (dialogType == HolidayManager.TYPE_WORKSWAP) week else -1, followWeekday = if (dialogType == HolidayManager.TYPE_WORKSWAP) weekday else -1, custom = true)
                    HolidayManager.save(context, year, all); reload(); CourseReminderHelper.startReminderService(context); showDialog = false
                    editingEntry = null
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }, colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.weight(1f))
                if (editingEntry != null) {
                    TextButton("删除", {
                        val old = editingEntry
                        if (old != null) {
                            HolidayManager.save(context, year, HolidayManager.load(context, year).filterNot { it.date == old.date && it.type == old.type && it.name == old.name })
                            reload(); CourseReminderHelper.startReminderService(context); showDialog = false; editingEntry = null
                        }
                    }, modifier = Modifier.weight(1f))
                }
                }
            }
        }
    }
}
