package com.haooz.chedule

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用入口 - 启动加速
 *
 * SharedPreferences 的实现在首次 getSharedPreferences() 时会开启后台线程异步加载 XML，
 * 但后续任意 getXXX() 都会 awaitLoadedLocked() 阻塞调用线程直到加载完成。
 * 冷启动时 MainActivity / 各 ViewModel 会在主线程同步读取大量配置键，
 * 如果此时 XML 尚未加载完（课程数据 JSON 可达数百 KB），主线程会被硬生生卡住。
 *
 * 这里在 Application 阶段就用后台线程把这些文件"碰"一次，把磁盘加载提前到
 * 主线程真正需要它们之前，从而让主线程的首次读取基本变成纯内存操作。
 */
class NexioApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        warmUpSharedPreferences()
    }

    /**
     * 异步预热高频 SharedPreferences 文件。
     * 只做触发加载，不读取具体值；加载本身在 SharedPreferences 内部线程完成，
     * 后台线程在此阻塞等待不影响主线程。
     */
    private fun warmUpSharedPreferences() {
        // 按启动路径上的命中顺序排列：最大的课程数据文件优先
        val names = arrayOf(
            "course_schedule_prefs",   // 课程 / 设置 / 搭配外观（体积最大）
            "app_preferences",         // 隐藏后台等主界面开关
            "course_reminder_prefs",   // 提醒开关（startReminderService 会读）
            "app_theme_prefs"          // 主题模式（首帧计算深浅色需要）
        )
        CoroutineScope(Dispatchers.IO).launch {
            names.forEach { name ->
                runCatching {
                    // 访问一次即触发 awaitLoaded()，加载在后台线程完成后返回
                    getSharedPreferences(name, MODE_PRIVATE).all
                }
            }
        }
    }
}
