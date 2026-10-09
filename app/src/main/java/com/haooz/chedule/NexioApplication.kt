package com.haooz.chedule

import android.app.Application
import com.haooz.chedule.data.asKeyValueStore
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

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
        // 跨平台存储入口：必须最先初始化，之后任何 AppStorage.store(...) 才可用。
        // 工厂按名字转发到各自的 SharedPreferences 文件 —— 名字由各业务文件自带，
        // 这里不做汇总，避免抄错名字导致存量数据读不出来。
        com.haooz.chedule.data.AppStorage.init { name ->
            getSharedPreferences(name, MODE_PRIVATE).asKeyValueStore()
        }
        // 应用版本号：跨平台侧（:core）无法读 PackageManager，启动时注入一次。
        // 上报负载里的 app_version 依赖它，漏掉会变成 "unknown"。
        com.haooz.chedule.data.AppInfo.init(
            runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull() ?: "unknown",
        )
        // 课表外观单例：尽早初始化，主界面首帧即可读壁纸与卡片参数
        com.haooz.chedule.data.ScheduleAppearance.init(this)
        // 预测性返回动画开关：应用启动即同步到全局单例（应用设置中可切换）
        PredictiveBackSettings.enabled = getSharedPreferences("app_preferences", MODE_PRIVATE)
            .getBoolean(PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION, true)
        // 应用材质档位：按钮/低栏等 chrome 折射随档降级
        com.haooz.chedule.ui.utils.AppMaterialSettings.load(this)
        com.haooz.chedule.ui.utils.CrashLogHelper.install(this)
        warmUpSharedPreferences()
        cleanupTransientFiles()
        initWearableSync()
        // 调休映射迁移：旧版存「第几周+星期几」，周次相对课表，换课表就错位。
        // 一次性换算成绝对日期（followDate），之后按当前课表实时折算。走后台线程避免拖慢冷启动。
        Thread {
            runCatching { com.haooz.chedule.data.HolidayManager.migrateLegacyFollowDates(this) }
        }.start()
    }

    /** 手表课表推送：启动注册，课程变更走 CourseRepository 监听 */
    private fun initWearableSync() {
        runCatching {
            com.haooz.chedule.wearable.WearableScheduleSync.init(this)
            val repo = com.haooz.chedule.data.CourseRepository.getInstance(this)
            repo.addCourseChangedListener { _, _ ->
                com.haooz.chedule.wearable.WearableScheduleSync.onScheduleChanged("course-change")
            }
        }
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

    /** 清理分享卡片与旧版更新 APK，避免 cache/files 无界膨胀 */
    private fun cleanupTransientFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val now = System.currentTimeMillis()
                val dayMs = 24L * 60 * 60 * 1000
                File(cacheDir, "share").listFiles()?.forEach { file ->
                    if (file.isFile &&
                        file.name.startsWith("share_") &&
                        now - file.lastModified() > dayMs
                    ) {
                        file.delete()
                    }
                }
                // APK 与检查更新同一策略：按 latest_tag 保留完整包，不按 mtime 只留最新
                com.haooz.chedule.ui.utils.UpdateChecker.cleanupTransientApks(this@NexioApplication)
            }
        }
    }
}
