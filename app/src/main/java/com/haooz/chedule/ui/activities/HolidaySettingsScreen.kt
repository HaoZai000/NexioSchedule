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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val listState = rememberLazyListState()
    val dropdownColors = top.yukonga.miuix.kmp.basic.DropdownDefaults.dropdownColors(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent
    )
    fun reload() { entries = HolidayManager.load(context, year) }
    Scaffold(topBar = {}) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().overScrollVertical().then(scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 72.dp, 16.dp, 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SmallTitle("数据管理", Modifier.offset((-15).dp))
                Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column {
                        val yearEntry = DropdownEntry((2024..2030).map { DropdownItem(it.toString(), it == year) { year = it; reload() } })
                        OverlayDropdownMenu(
                            entry = yearEntry,
                            title = "年份",
                            summary = year.toString(),
                            collapseOnSelection = true,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            dropdownColors = dropdownColors,
                        )
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton("获取网络数据", { loading = true; scope.launch(Dispatchers.IO) {
                                val result = runCatching {
                                    val conn = URL("https://unpkg.com/holiday-calendar@1.3.0/data/CN/$year.json").openConnection() as HttpURLConnection
                                    conn.connectTimeout = 10_000; conn.readTimeout = 10_000
                                    val text = conn.inputStream.bufferedReader().use { it.readText() }; conn.disconnect(); HolidayManager.parseApiResponse(text)
                                }.getOrDefault(emptyList())
                                withContext(Dispatchers.Main) { HolidayManager.mergeApiEntries(context, year, result); reload(); loading = false; Toast.makeText(context, if (result.isEmpty()) "获取失败或暂无数据" else "已更新 ${result.size} 条记录", Toast.LENGTH_SHORT).show() }
                            } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                            TextButton("清空", { HolidayManager.clear(context, year); reload(); CourseReminderHelper.startReminderService(context) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item { SmallTitle("节假日与调休", Modifier.offset((-15).dp)) }
            if (entries.isEmpty()) item { Text(if (loading) "正在获取…" else "暂无数据，请获取网络数据或手动添加") }
            items(entries, key = { "${it.date}-${it.type}-${it.name}" }) { entry ->
                Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    ArrowPreference(
                        title = entry.name,
                        summary = if (entry.type == HolidayManager.TYPE_HOLIDAY) "节假日 · ${entry.date}${if (entry.endDate.isNotBlank()) " 至 ${entry.endDate}" else ""}" else "调休工作日 · ${entry.date} · 第${entry.followWeek}周${entry.followWeekday}日",
                        onClick = {}
                    )
                }
            }
            item {
                Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    Column {
                        ArrowPreference("添加节假日", "当天不发送课程提醒", onClick = { dialogType = HolidayManager.TYPE_HOLIDAY; date = "$year-01-01"; name = ""; showDialog = true })
                        ArrowPreference("添加调休工作日", "按指定周次和星期匹配课程", onClick = { dialogType = HolidayManager.TYPE_WORKSWAP; date = "$year-01-01"; name = ""; showDialog = true })
                    }
                }
            }
        }
    }
    if (showDialog) {
        top.yukonga.miuix.kmp.overlay.OverlayDialog(
            title = if (dialogType == HolidayManager.TYPE_HOLIDAY) "添加节假日" else "添加调休工作日",
            summary = "使用 yyyy-MM-dd 格式填写日期",
            show = true, liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NativeMiuixTextField(date, { date = it }, label = "日期", modifier = Modifier.fillMaxWidth())
                NativeMiuixTextField(name, { name = it }, label = "名称", modifier = Modifier.fillMaxWidth())
                if (dialogType == HolidayManager.TYPE_WORKSWAP) {
                    NativeMiuixTextField(followWeek, { followWeek = it.filter(Char::isDigit) }, label = "跟随周次", modifier = Modifier.fillMaxWidth())
                    NativeMiuixTextField(followWeekday, { followWeekday = it.filter(Char::isDigit) }, label = "跟随星期（1-7）", modifier = Modifier.fillMaxWidth())
                }
                TextButton("保存", {
                    val valid = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
                    if (!valid) { Toast.makeText(context, "日期格式不正确", Toast.LENGTH_SHORT).show(); return@TextButton }
                    val all = HolidayManager.load(context, year).toMutableList()
                    all += HolidayManager.Entry(date, name = name.ifBlank { "节假日" }, type = dialogType, followWeek = followWeek.toIntOrNull() ?: -1, followWeekday = followWeekday.toIntOrNull() ?: -1, custom = true)
                    HolidayManager.save(context, year, all); reload(); CourseReminderHelper.startReminderService(context); showDialog = false
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }, colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
