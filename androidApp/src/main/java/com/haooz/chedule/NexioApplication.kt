package com.haooz.chedule

import android.app.Application
import com.haooz.chedule.data.appearance.AppearanceStore
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.platform.AppFiles
import com.haooz.chedule.data.platform.AppInfo
import com.haooz.chedule.data.platform.FileAppFile
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.ScheduleEffects
import com.haooz.chedule.data.platform.initToast
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.LegacyStorageMigration
import com.haooz.chedule.data.storage.SqliteStorage
import com.haooz.chedule.platform.AndroidScheduleEffects
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 存储与平台能力接线。运行时只有 `nexio.db` 一份数据：SharedPreferences 只在
 * [com.haooz.chedule.data.storage.LegacyStorageMigration] 里作为迁移源出现一次，迁完即删。
 */
class NexioApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 必须最先初始化。工厂按名转发命名空间，不在此汇总键名。
        // 没有第二套存储：库不可用时直接抛，不静默降级。
        AppStorage.init { name ->
            SqliteStorage.store(context = this, namespace = name)
        }
        // 课程行表接线。取不到就抛，不再退回 JSON。
        CourseStorage.install { SqliteStorage.courseRows(this) }
        // 旧数据全部搬进 nexio.db，然后删光旧存储。同步做完：这里返回时 shared_prefs 已不存在。
        com.haooz.chedule.data.storage.LegacyStorageMigration.run(this)
        // 小部件/通知在首帧前取词依赖它。
        com.haooz.chedule.i18n.AppLanguage.init(com.haooz.chedule.i18n.LanguageStore.load())
        // :core 只认 showToast(...)，Android 在此接线。用 applicationContext 避免 Toast 挂住 Activity 实例；时长语义与迁移前一致。
        com.haooz.chedule.data.platform.initToast { message, long ->
            android.widget.Toast.makeText(
                this,
                message,
                if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        // :core 无法读 PackageManager，启动时注入版本号；上报负载的 app_version 依赖它，漏掉会成 "unknown"。
        AppInfo.init(
            runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull() ?: "unknown",
        )
        // :core 只认接口，Android 用 File 实现。readAsset 走 assets 供内置学校索引首启引导；失败让它抛，由 SchoolRepository 打日志跳过引导。
        AppFiles.init(
            root = com.haooz.chedule.data.platform.FileAppFile(filesDir),
            readAsset = { name -> assets.open(name).use { it.readBytes() } },
        )
        // 尽早初始化，主界面首帧可读取壁纸与卡片参数。
        com.haooz.chedule.data.ScheduleAppearance.init(this)
        // WallpaperStore 走「接口 + 启动注入」，Android 原样委托 ScheduleAppearance（降采样/LruCache/WebP 80 口径不变）。必须在 ScheduleAppearance.init 之后。
        val wallpaperBackend = com.haooz.chedule.data.AndroidWallpaperBackend()
        com.haooz.chedule.data.WallpaperStore.backend = wallpaperBackend
        val wallpaperPreload = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "nexio-wallpaper-preload")
        }.submit(java.util.concurrent.Callable {
            AppearanceStore.currentStyle()
            runCatching { wallpaperBackend.loadNow() }.getOrNull()
        })
        com.haooz.chedule.data.WallpaperStore.synchronousLoader = {
            runCatching { wallpaperPreload.get(3, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull()
        }
        // 课程变更后的平台副作用
        ScheduleEffects.init(AndroidScheduleEffects(this))
        // 同步预测性返回动画开关到全局单例。
        PredictiveBackSettings.enabled = AppStorage.store("app_preferences")
            .getBoolean(PredictiveBackSettings.KEY_PREDICTIVE_BACK_ANIMATION, true)
        // 加载应用材质档位。
        com.haooz.chedule.ui.utils.AppMaterialSettings.load()
        com.haooz.chedule.ui.utils.CrashLogHelper.install(this)
        // FeatureLog 已抽 :core，轨迹记录在此接回 CrashLogHelper。必须在 install 之后；未注入静默丢弃但录制轨迹会丢。
        com.haooz.chedule.ui.utils.FeatureLog.init(
            trace = { feature, event, detail ->
                com.haooz.chedule.ui.utils.CrashLogHelper.trace(feature, event, detail)
            },
            recording = { feature, event, detail ->
                com.haooz.chedule.ui.utils.CrashLogHelper.traceRecording(feature, event, detail)
            },
            isRecording = { com.haooz.chedule.ui.utils.CrashLogHelper.isRecording },
        )
        // 莫奈取色开关或系统壁纸色变化时重刷小部件
        com.haooz.chedule.widget.WidgetMonetColors.installRefresh(this)
        warmUpStorage()
        cleanupTransientFiles()
        initWearableSync()
        enableWebViewRemoteDebugging()
        // 调休映射迁移：旧版存「周次+星期」相对课表会错位，换算成绝对日期后按当前课表实时折算。后台线程执行避免拖慢冷启动。
        Thread {
            // 日期解析在此注入（:core 不能反向依赖 :app 的 CourseRepository）。仓储构造失败则整段跳过，与原实现一致。
            runCatching {
                val repository = CourseRepository()
                HolidayManager.migrateLegacyFollowDates { week, weekday ->
                    runCatching { repository.dateForTeachingWeekDay(week, weekday) }.getOrNull()
                }
            }
        }.start()
    }

    /** 仅 debuggable 包开启 WebView 远程调试（chrome://inspect），排查资源加载问题时可直接看到每个请求状态码/MIME。 */
    private fun enableWebViewRemoteDebugging() {
        runCatching {
            val debuggable =
                (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (debuggable) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    /** 手表课表推送：启动注册，课程变更走 CourseRepository 监听 */
    private fun initWearableSync() {
        runCatching {
            com.haooz.chedule.wearable.WearableScheduleSync.init(this)
            val repo = CourseRepository.getInstance()
            repo.addCourseChangedListener { _, _ ->
                com.haooz.chedule.wearable.WearableScheduleSync.onScheduleChanged("course-change")
            }
        }
    }

    /**
     * 存储启动预热（迁移已在 [onCreate] 同步做完）：把热路径命名空间放进 [AppStorage] 缓存，
     * 免得 Receiver / 小部件第一次取用时在各自线程上走加锁与 meta 查询。全部在 IO 线程。
     */
    private fun warmUpStorage() {
        CoroutineScope(Dispatchers.IO).launch {
            HOT_NAMESPACES.forEach { name -> runCatching { AppStorage.store(name) } }

            // 课程读取预热（行表已在迁移里建好，这里只是把首屏要用的数据读进缓存）
            runCatching {
                val repository = CourseRepository()
                repository.getScheduleNames().forEach { schedule ->
                    runCatching { repository.getCoursesForSchedule(schedule) }
                }
            }.onFailure { NexioLog.w(TAG, "课程预热失败（首屏会再读一次）", it) }

            // 课表设置：首帧要用的周次 / 开学日 / 重组规则才会第一次碰行表（settingsOf + 同步标记），
            // 在这里读掉，主线程首次组合就只剩查缓存。
            runCatching {
                val repository = CourseRepository()
                repository.getTeachingWeekReorganizations()
                repository.getTotalWeeks()
                repository.getCurrentWeek()
                repository.getClassStartTime()
                repository.getCurrentSectionTimes()
            }.onFailure { NexioLog.w(TAG, "课表设置预热失败（首屏会再读一次）", it) }

            // 节假日：首帧的 holidayEntriesByYear 是「整份偏好快照 + 逐年反序列化」，
            // 预热后直接命中它的快照缓存。两级剔除配置没有缓存（几次内存读），不值得预热。
            runCatching {
                HolidayManager.loadAllByYear()
            }.onFailure { NexioLog.w(TAG, "节假日预热失败（首屏会再读一次）", it) }
        }
    }

    private companion object {
        const val TAG = "NexioApplication"

        /**
         * 启动路径会命中的命名空间。后几项由 Receiver / Service 首次访问，
         * 在迁移里已经完成导入，这里只是把实例提前放进 [AppStorage] 缓存。
         * 新增偏好文件时**不必**加到这里（迁移是全量扫描），只影响「第一次读要不要加个锁」。
         */
        val HOT_NAMESPACES = arrayOf(
            "appearance_settings",     // 壁纸变换 + 卡片参数
            "course_schedule_prefs",   // 课程 / 设置（结构最复杂）
            "app_preferences",         // 隐藏后台等主界面开关
            "course_reminder_prefs",   // 提醒开关（startReminderService 会读）
            "app_theme_prefs",         // 主题模式（首帧计算深浅色需要）
            "countdown_state",         // 实况倒计时状态（每分钟由小部件广播驱动）
            "island_countdown_state",  // 超级岛状态（真实）
            "island_countdown_state_test",
            "reminder_sent_history",   // 提醒去重
            "reminder_alarm_registry", // 闹钟 requestCode 登记
            "day_change_state",
            "crash_log_prefs",         // 崩溃日志录制状态
        )
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
                // APK 沿用检查更新策略：按 latest_tag 保留完整包，不按 mtime 只留最新。
                com.haooz.chedule.ui.utils.UpdateChecker.cleanupTransientApks(this@NexioApplication)
            }
        }
    }
}
