/** Android 桥接层 - WebView 与原生代码通信接口 */
package com.haooz.chedule.ui.web

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.Course
import java.util.UUID

private const val TAG = "AndroidBridge"

/**
 * AndroidBridge：处理 WebView 与 Native 代码的通信
 * JS 脚本通过 window.AndroidBridge 调用这些方法
 */
class AndroidBridge(
    private val context: Context,
    private val webView: WebView,
    private val onCourseImported: (List<Course>) -> Unit,
    private val onTaskCompleted: () -> Unit,
    private val onShowAlert: ((title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    private val onShowPrompt: ((title: String, tip: String, defaultText: String, validatorJs: String, onResult: (String?) -> Unit) -> Unit)? = null,
    private val onShowSingleSelection: ((title: String, items: List<String>, defaultIndex: Int, onResult: (Int?) -> Unit) -> Unit)? = null
) {
    private val gson: Gson = GsonBuilder()
        .create()
    private val handler = Handler(Looper.getMainLooper())
    private val nameColorMap = mutableMapOf<String, Long>()
    private var colorIndex = 0
    private var currentToast: Toast? = null

    /** JS 调用：显示短暂的 Toast 消息 */
    @JavascriptInterface
    fun showToast(message: String) {
        handler.post {
            currentToast?.cancel()
            val newToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            newToast.show()
            currentToast = newToast
        }
    }

    /** JS 调用：显示 Alert 弹窗 */
    @JavascriptInterface
    fun showAlert(titleText: String, contentText: String, confirmText: String, promiseId: String) {
        handler.post {
            val callback = onShowAlert
            if (callback != null) {
                callback(titleText, contentText, confirmText) { confirmed ->
                    resolveJsPromise(promiseId, if (confirmed) "true" else "false")
                }
            } else {
                resolveJsPromise(promiseId, "true")
            }
        }
    }

    /** JS 调用：显示 Prompt 弹窗 */
    @JavascriptInterface
    fun showPrompt(
        titleText: String,
        tipText: String,
        defaultText: String,
        validatorJsFunction: String,
        promiseId: String
    ) {
        handler.post {
            val callback = onShowPrompt
            if (callback != null) {
                callback(titleText, tipText, defaultText, validatorJsFunction) { input ->
                    if (input != null) {
                        if (validatorJsFunction.isNullOrEmpty()) {
                            val escaped = input.replace("'", "\\'")
                            resolveJsPromise(promiseId, "'$escaped'")
                        } else {
                            val jsScript = "javascript:${validatorJsFunction}('${input.replace("'", "\\'")}')"
                            webView.evaluateJavascript(jsScript) { result ->
                                val error = result?.trim('"')
                                if (error.isNullOrEmpty() || error.equals("false", ignoreCase = true)) {
                                    val escaped = input.replace("'", "\\'")
                                    resolveJsPromise(promiseId, "'$escaped'")
                                } else {
                                    handler.post {
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                    rejectJsPromise(promiseId, error)
                                }
                            }
                        }
                    } else {
                        resolveJsPromise(promiseId, "null")
                    }
                }
            } else {
                val escapedInput = defaultText.replace("'", "\\'")
                resolveJsPromise(promiseId, "'$escapedInput'")
            }
        }
    }

    /** JS 调用：显示单选列表弹窗 */
    @JavascriptInterface
    fun showSingleSelection(
        titleText: String,
        itemsJsonString: String,
        defaultSelectedIndex: Int,
        promiseId: String
    ) {
        handler.post {
            try {
                val items = gson.fromJson<List<String>>(
                    itemsJsonString,
                    object : TypeToken<List<String>>() {}.type
                )
                val callback = onShowSingleSelection
                if (callback != null) {
                    callback(titleText, items, defaultSelectedIndex) { selectedIndex ->
                        if (selectedIndex != null) {
                            resolveJsPromise(promiseId, selectedIndex.toString())
                        } else {
                            resolveJsPromise(promiseId, "null")
                        }
                    }
                } else {
                    resolveJsPromise(promiseId, defaultSelectedIndex.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析单选列表失败: ${e.message}", e)
                Toast.makeText(context, "选项数据错误", Toast.LENGTH_LONG).show()
                rejectJsPromise(promiseId, "选项列表 JSON 无效: ${e.message}")
            }
        }
    }

    /** JS 调用：将课程数据传回 Android 端进行保存 */
    @JavascriptInterface
    fun saveImportedCourses(coursesJsonString: String, promiseId: String) {
        Log.d(TAG, "接收到课程数据，大小: ${coursesJsonString.length / 1024} KB")
        handler.post {
            try {
                val importedCourses = gson.fromJson<List<ImportCourseJsonModel>>(
                    coursesJsonString,
                    object : TypeToken<List<ImportCourseJsonModel>>() {}.type
                )

                if (importedCourses.isNullOrEmpty()) {
                    Toast.makeText(context, "导入失败：未解析到课程数据", Toast.LENGTH_LONG).show()
                    rejectJsPromise(promiseId, "课程数据为空")
                    return@post
                }

                val courses = importedCourses.map { json ->
                    val color = json.color?.toLong()
                        ?: getOrAssignColorByName(json.name)
                    Course(
                        id = json.id ?: UUID.randomUUID().toString(),
                        name = json.name,
                        classroom = json.position,
                        teacher = json.teacher,
                        dayOfWeek = json.day,
                        startSection = json.startSection ?: 1,
                        endSection = json.endSection ?: json.startSection ?: 1,
                        startWeek = json.weeks.minOrNull() ?: 1,
                        endWeek = json.weeks.maxOrNull() ?: 20,
                        weekType = Course.WEEK_TYPE_ALL,
                        selectedWeeks = json.weeks.sorted(),
                        colorRes = color
                    )
                }

                Toast.makeText(context, "课程导入成功！共 ${courses.size} 门课程", Toast.LENGTH_SHORT).show()
                onCourseImported(courses)
                resolveJsPromise(promiseId, "true")
            } catch (e: Exception) {
                Log.e(TAG, "课程导入解析失败", e)
                Log.e(TAG, "原始JSON: $coursesJsonString")
                Toast.makeText(context, "课程导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                rejectJsPromise(promiseId, "课程导入失败: ${e.message}")
            }
        }
    }

    /** JS 调用：将课表配置数据传回 Android 端进行保存 */
    @JavascriptInterface
    fun saveCourseConfig(configJsonString: String, promiseId: String) {
        Log.d(TAG, "接收到课表配置数据，大小: ${configJsonString.length} 字节")
        handler.post {
            try {
                val config = gson.fromJson(configJsonString, CourseConfigJsonModel::class.java)
                Log.d(TAG, "课表配置解析成功: semesterStartDate=${config.semesterStartDate}, totalWeeks=${config.semesterTotalWeeks}")

                val prefs = context.getSharedPreferences("edu_import_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("semester_start_date", config.semesterStartDate)
                    putInt("semester_total_weeks", config.semesterTotalWeeks)
                    putInt("default_class_duration", config.defaultClassDuration)
                    putInt("default_break_duration", config.defaultBreakDuration)
                    putInt("first_day_of_week", config.firstDayOfWeek)
                    apply()
                }

                Toast.makeText(context, "课表配置导入成功", Toast.LENGTH_SHORT).show()
                resolveJsPromise(promiseId, "true")
            } catch (e: Exception) {
                Log.e(TAG, "课表配置解析失败: ${e.message}", e)
                Toast.makeText(context, "配置导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                rejectJsPromise(promiseId, "配置导入失败: ${e.message}")
            }
        }
    }

    /** JS 调用：将预设时间段数据传回 Android 端进行保存 */
    @JavascriptInterface
    fun savePresetTimeSlots(timeSlotsJsonString: String, promiseId: String) {
        Log.d(TAG, "接收到预设时间段数据，大小: ${timeSlotsJsonString.length / 1024} KB")
        handler.post {
            try {
                val timeSlots = gson.fromJson<List<TimeSlotJsonModel>>(
                    timeSlotsJsonString,
                    object : TypeToken<List<TimeSlotJsonModel>>() {}.type
                )
                Log.d(TAG, "时间段数据解析成功: ${timeSlots.size} 个时间段")

                val json = Gson().toJson(timeSlots)
                val prefs = context.getSharedPreferences("edu_import_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("preset_time_slots", json).apply()

                Toast.makeText(context, "时间段导入成功", Toast.LENGTH_SHORT).show()
                resolveJsPromise(promiseId, "true")
            } catch (e: Exception) {
                Log.e(TAG, "时间段数据解析失败: ${e.message}", e)
                Toast.makeText(context, "时间段导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                rejectJsPromise(promiseId, "时间段导入失败: ${e.message}")
            }
        }
    }

    /** JS 调用：通知 Native 端 JS 任务已逻辑完成 */
    @JavascriptInterface
    fun notifyTaskCompletion() {
        handler.post {
            onTaskCompleted()
        }
    }

    private fun getOrAssignColorByName(courseName: String): Long {
        val trimmedName = courseName.trim()
        val existingColor = nameColorMap[trimmedName]
        if (existingColor != null) return existingColor

        val color = Course.courseColors[colorIndex % Course.courseColors.size]
        colorIndex++
        nameColorMap[trimmedName] = color
        return color
    }

    private fun resolveJsPromise(promiseId: String, result: String) {
        webView.evaluateJavascript("window._resolveAndroidPromise('$promiseId', $result);", null)
    }

    private fun rejectJsPromise(promiseId: String, error: String) {
        val escapedError = error.replace("'", "\\'")
        webView.evaluateJavascript("window._rejectAndroidPromise('$promiseId', '$escapedError');", null)
    }

    data class ImportCourseJsonModel(
        val id: String? = null,
        val name: String,
        val teacher: String = "",
        val position: String = "",
        val day: Int,
        val startSection: Int? = null,
        val endSection: Int? = null,
        val weeks: List<Int> = emptyList(),
        val isCustomTime: Boolean = false,
        val customStartTime: String? = null,
        val customEndTime: String? = null,
        val color: Int? = null,
        val remark: String? = null
    )

    data class CourseConfigJsonModel(
        val semesterStartDate: String? = null,
        val semesterTotalWeeks: Int = 20,
        val defaultClassDuration: Int = 45,
        val defaultBreakDuration: Int = 10,
        val firstDayOfWeek: Int = 1
    )

    data class TimeSlotJsonModel(
        val number: Int,
        val startTime: String,
        val endTime: String,
        val alias: String? = null
    )
}
