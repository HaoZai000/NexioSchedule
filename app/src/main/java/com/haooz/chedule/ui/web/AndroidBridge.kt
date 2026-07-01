package com.haooz.chedule.ui.web

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.google.gson.Gson
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
    private val onTaskCompleted: () -> Unit
) {
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private val nameColorMap = mutableMapOf<String, Long>()
    private var colorIndex = 0

    /** JS 调用：显示短暂的 Toast 消息 */
    @JavascriptInterface
    fun showToast(message: String) {
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /** JS 调用：显示 Alert 弹窗 */
    @JavascriptInterface
    fun showAlert(titleText: String, contentText: String, confirmText: String, promiseId: String) {
        handler.post {
            // 简化实现：直接确认
            resolveJsPromise(promiseId, "true")
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
            // 简化实现：使用默认值
            val escapedInput = defaultText.replace("'", "\\'")
            resolveJsPromise(promiseId, "'$escapedInput'")
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
                // 简化实现：使用默认选项
                resolveJsPromise(promiseId, defaultSelectedIndex.toString())
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

                // 转换为应用的 Course 模型
                val courses = importedCourses.map { json ->
                    val color = getOrAssignColorByName(json.name)
                    Course(
                        id = UUID.randomUUID().toString(),
                        name = json.name,
                        classroom = json.position,
                        teacher = json.teacher,
                        dayOfWeek = json.day,
                        startSection = json.startSection,
                        endSection = json.endSection,
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
                e.printStackTrace()
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
                // 解析配置（当前简化处理，忽略配置）
                Toast.makeText(context, "课表配置已接收", Toast.LENGTH_SHORT).show()
                resolveJsPromise(promiseId, "true")
            } catch (e: Exception) {
                e.printStackTrace()
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
                // 解析时间段（当前简化处理）
                Toast.makeText(context, "时间段数据已接收", Toast.LENGTH_SHORT).show()
                resolveJsPromise(promiseId, "true")
            } catch (e: Exception) {
                e.printStackTrace()
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

    /**
     * 根据课程名分配颜色（同名课程同色）
     */
    private fun getOrAssignColorByName(courseName: String): Long {
        val trimmedName = courseName.trim()
        val existingColor = nameColorMap[trimmedName]
        if (existingColor != null) return existingColor

        val color = Course.courseColors[colorIndex % Course.courseColors.size]
        colorIndex++
        nameColorMap[trimmedName] = color
        return color
    }

    /** 在 JS 环境中解决 Promise */
    private fun resolveJsPromise(promiseId: String, result: String) {
        handler.post {
            webView.evaluateJavascript("window._resolveAndroidPromise('$promiseId', $result);", null)
        }
    }

    /** 在 JS 环境中拒绝 Promise */
    private fun rejectJsPromise(promiseId: String, error: String) {
        handler.post {
            val escapedError = error.replace("'", "\\'")
            webView.evaluateJavascript("window._rejectAndroidPromise('$promiseId', '$escapedError');", null)
        }
    }

    /**
     * 课程导入 JSON 模型
     * 对应 shiguang_warehouse 脚本输出的课程格式
     */
    data class ImportCourseJsonModel(
        val name: String,
        val teacher: String = "",
        val position: String = "",
        val day: Int,
        val startSection: Int,
        val endSection: Int,
        val weeks: List<Int> = emptyList()
    )
}
